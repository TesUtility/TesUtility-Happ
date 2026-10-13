package com.example.service

import android.net.ProxyInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.random.Random

object VlessTunnelManager {

    private const val TAG = "VlessTunnelManager"
    const val LOCAL_PROXY_PORT = 10809
    const val LOCAL_SOCKS_PORT = 10808

    /**
     * Verifies real TCP connectivity to the remote server host:port using a protected socket.
     * Returns Pair(isReachable, latencyMs).
     */
    suspend fun verifyServerReachability(
        host: String,
        port: Int,
        protectSocket: (Socket) -> Boolean
    ): Pair<Boolean, Long> {
        val startTime = System.currentTimeMillis()
        return try {
            val address = InetAddress.getByName(host.trim())
            Socket().use { socket ->
                protectSocket(socket)
                socket.soTimeout = 4000
                socket.connect(InetSocketAddress(address, port), 4000)
                val latency = System.currentTimeMillis() - startTime
                Pair(true, latency)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server reachability check failed for $host:$port: ${e.message}")
            Pair(false, -1L)
        }
    }

    /**
     * Runs a local HTTP CONNECT proxy on 127.0.0.1:10809 to bridge device traffic.
     */
    fun startLocalProxyServer(
        protectSocket: (Socket) -> Boolean,
        onTrafficDelta: (Long, Long) -> Unit
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            var serverSocket: ServerSocket? = null
            try {
                serverSocket = ServerSocket(LOCAL_PROXY_PORT, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket.reuseAddress = true
                Log.i(TAG, "Local proxy listening on 127.0.0.1:$LOCAL_PROXY_PORT")

                while (isActive) {
                    val clientSocket = try {
                        serverSocket.accept()
                    } catch (_: Exception) {
                        break
                    }

                    launch(Dispatchers.IO) {
                        handleProxyClient(clientSocket, protectSocket, onTrafficDelta)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Local proxy server error: ${e.message}")
            } finally {
                try {
                    serverSocket?.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleProxyClient(
        clientSocket: Socket,
        protectSocket: (Socket) -> Boolean,
        onTrafficDelta: (Long, Long) -> Unit
    ) {
        var remoteSocket: Socket? = null
        try {
            clientSocket.soTimeout = 10000
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val requestLine = readLineFromStream(clientIn) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val target = parts[1]

            // Drain headers until blank line
            while (true) {
                val header = readLineFromStream(clientIn) ?: break
                if (header.isEmpty()) break
            }

            if (method == "CONNECT") {
                val targetHost = target.substringBefore(":")
                val targetPort = target.substringAfter(":", "443").toIntOrNull() ?: 443

                remoteSocket = Socket()
                protectSocket(remoteSocket)
                remoteSocket.connect(InetSocketAddress(targetHost, targetPort), 7000)

                // Respond 200 Connection Established to client
                val resp = "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII)
                clientOut.write(resp)
                clientOut.flush()

                val remoteIn = remoteSocket.getInputStream()
                val remoteOut = remoteSocket.getOutputStream()

                // Bidirectional relay
                val jobClientToRemote = CoroutineScope(Dispatchers.IO).launch {
                    pipeStreams(clientIn, remoteOut) { bytes -> onTrafficDelta(bytes, 0) }
                }
                pipeStreams(remoteIn, clientOut) { bytes -> onTrafficDelta(0, bytes) }
                jobClientToRemote.cancel()

            } else {
                // Standard HTTP GET/POST forward
                val urlHost = target.removePrefix("http://").substringBefore("/").substringBefore(":")
                val urlPort = if (target.contains(":")) target.substringAfter(":").substringBefore("/").toIntOrNull() ?: 80 else 80

                remoteSocket = Socket()
                protectSocket(remoteSocket)
                remoteSocket.connect(InetSocketAddress(urlHost, urlPort), 7000)

                val remoteIn = remoteSocket.getInputStream()
                val remoteOut = remoteSocket.getOutputStream()

                remoteOut.write("$requestLine\r\n\r\n".toByteArray(Charsets.UTF_8))
                remoteOut.flush()

                val jobClientToRemote = CoroutineScope(Dispatchers.IO).launch {
                    pipeStreams(clientIn, remoteOut) { bytes -> onTrafficDelta(bytes, 0) }
                }
                pipeStreams(remoteIn, clientOut) { bytes -> onTrafficDelta(0, bytes) }
                jobClientToRemote.cancel()
            }
        } catch (_: Exception) {
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
            try { remoteSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun readLineFromStream(inputStream: InputStream): String? {
        val sb = StringBuilder()
        var b: Int
        while (inputStream.read().also { b = it } != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
            if (sb.length > 2048) break
        }
        return if (sb.isEmpty() && b == -1) null else sb.toString()
    }

    private fun pipeStreams(
        input: InputStream,
        output: OutputStream,
        onBytesCopied: (Long) -> Unit
    ) {
        val buffer = ByteArray(8192)
        try {
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                output.flush()
                onBytesCopied(read.toLong())
            }
        } catch (_: Exception) {}
    }

    /**
     * Reads from the TUN file descriptor and handles DNS (UDP 53) and ICMP Ping packets.
     * Prevents apps and system network health checks from hanging.
     */
    fun startTunWorker(
        vpnInterface: ParcelFileDescriptor,
        protectDatagramSocket: (DatagramSocket) -> Boolean,
        onTrafficDelta: (Long, Long) -> Unit
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            val inStream = FileInputStream(vpnInterface.fileDescriptor)
            val outStream = FileOutputStream(vpnInterface.fileDescriptor)
            val packetBuffer = ByteArray(32768)

            var dnsSocket: DatagramSocket? = null
            try {
                dnsSocket = DatagramSocket()
                protectDatagramSocket(dnsSocket)
                dnsSocket.soTimeout = 3000
            } catch (e: Exception) {
                Log.w(TAG, "Could not create protected DNS socket: ${e.message}")
            }

            try {
                while (isActive) {
                    val length = try {
                        inStream.read(packetBuffer)
                    } catch (_: Exception) {
                        break
                    }
                    if (length <= 0) continue

                    onTrafficDelta(length.toLong(), 0)

                    // Inspect IPv4 packet
                    val version = (packetBuffer[0].toInt() shr 4) and 0x0F
                    if (version == 4 && length >= 20) {
                        val headerLength = (packetBuffer[0].toInt() and 0x0F) * 4
                        val protocol = packetBuffer[9].toInt() and 0xFF

                        if (protocol == 1) {
                            // ICMP (Echo Request -> Echo Reply)
                            if (length >= headerLength + 8 && packetBuffer[headerLength].toInt() == 8) {
                                handleIcmpEcho(packetBuffer, length, headerLength, outStream)
                                onTrafficDelta(0, length.toLong())
                            }
                        } else if (protocol == 17 && dnsSocket != null) {
                            // UDP (Check for DNS port 53)
                            if (length >= headerLength + 8) {
                                val destPort = ((packetBuffer[headerLength + 2].toInt() and 0xFF) shl 8) or
                                        (packetBuffer[headerLength + 3].toInt() and 0xFF)
                                if (destPort == 53) {
                                    handleDnsQuery(packetBuffer, length, headerLength, dnsSocket, outStream)
                                    onTrafficDelta(0, 128)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { dnsSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun handleIcmpEcho(
        packet: ByteArray,
        length: Int,
        headerLength: Int,
        outStream: FileOutputStream
    ) {
        try {
            val reply = packet.copyOf(length)
            // Swap Source IP (12..15) and Destination IP (16..19)
            for (i in 0..3) {
                val temp = reply[12 + i]
                reply[12 + i] = reply[16 + i]
                reply[16 + i] = temp
            }

            // Change Type 8 (Echo Request) to Type 0 (Echo Reply)
            reply[headerLength] = 0

            // Recompute ICMP Checksum
            reply[headerLength + 2] = 0
            reply[headerLength + 3] = 0
            val icmpChecksum = computeChecksum(reply, headerLength, length - headerLength)
            reply[headerLength + 2] = ((icmpChecksum shr 8) and 0xFF).toByte()
            reply[headerLength + 3] = (icmpChecksum and 0xFF).toByte()

            // Recompute IP Checksum
            reply[10] = 0
            reply[11] = 0
            val ipChecksum = computeChecksum(reply, 0, headerLength)
            reply[10] = ((ipChecksum shr 8) and 0xFF).toByte()
            reply[11] = (ipChecksum and 0xFF).toByte()

            outStream.write(reply, 0, length)
            outStream.flush()
        } catch (_: Exception) {}
    }

    private fun handleDnsQuery(
        packet: ByteArray,
        length: Int,
        headerLength: Int,
        dnsSocket: DatagramSocket,
        outStream: FileOutputStream
    ) {
        try {
            val udpLength = ((packet[headerLength + 4].toInt() and 0xFF) shl 8) or
                    (packet[headerLength + 5].toInt() and 0xFF)
            val dnsPayloadOffset = headerLength + 8
            val dnsPayloadLength = udpLength - 8

            if (dnsPayloadLength > 0 && dnsPayloadOffset + dnsPayloadLength <= length) {
                val queryPacket = DatagramPacket(
                    packet,
                    dnsPayloadOffset,
                    dnsPayloadLength,
                    InetAddress.getByName("1.1.1.1"),
                    53
                )
                dnsSocket.send(queryPacket)

                val responseBuffer = ByteArray(1024)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                dnsSocket.receive(responsePacket)

                val respLength = responsePacket.length
                val newPacket = ByteArray(headerLength + 8 + respLength)

                // Copy IP Header
                System.arraycopy(packet, 0, newPacket, 0, headerLength)
                // Swap IP src and dst
                for (i in 0..3) {
                    newPacket[12 + i] = packet[16 + i]
                    newPacket[16 + i] = packet[12 + i]
                }
                // IP Total Length
                val totalLength = headerLength + 8 + respLength
                newPacket[2] = ((totalLength shr 8) and 0xFF).toByte()
                newPacket[3] = (totalLength and 0xFF).toByte()

                // Swap UDP Ports
                newPacket[headerLength] = packet[headerLength + 2]
                newPacket[headerLength + 1] = packet[headerLength + 3]
                newPacket[headerLength + 2] = packet[headerLength]
                newPacket[headerLength + 3] = packet[headerLength + 1]

                // UDP Length
                val newUdpLen = 8 + respLength
                newPacket[headerLength + 4] = ((newUdpLen shr 8) and 0xFF).toByte()
                newPacket[headerLength + 5] = (newUdpLen and 0xFF).toByte()
                newPacket[headerLength + 6] = 0
                newPacket[headerLength + 7] = 0

                // Copy DNS response payload
                System.arraycopy(responseBuffer, 0, newPacket, headerLength + 8, respLength)

                // Recompute IP Checksum
                newPacket[10] = 0
                newPacket[11] = 0
                val ipChk = computeChecksum(newPacket, 0, headerLength)
                newPacket[10] = ((ipChk shr 8) and 0xFF).toByte()
                newPacket[11] = (ipChk and 0xFF).toByte()

                outStream.write(newPacket)
                outStream.flush()
            }
        } catch (_: Exception) {}
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }
}

package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.db.AppDatabase
import com.example.model.VlessConfig
import com.example.parser.VlessParser
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class VlessVpnService : VpnService(), V2RayVPNServiceSupportsSet {

    companion object {
        private const val TAG = "VlessVpnService"
        const val ACTION_START = "com.example.service.VlessVpnService.START"
        const val ACTION_STOP = "com.example.service.VlessVpnService.STOP"
        const val EXTRA_CONFIG_ID = "EXTRA_CONFIG_ID"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vless_vpn_service_channel"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var v2rayPoint: V2RayPoint? = null
    private var trafficJob: Job? = null
    private var proxyJob: Job? = null
    private var tunWorkerJob: Job? = null

    private var connectionStartTime: Long = 0
    private val totalSentBytes = AtomicLong(0)
    private val totalRecvBytes = AtomicLong(0)
    private var isForegroundActive = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            Libv2ray.initV2Env(filesDir.absolutePath)
            v2rayPoint = Libv2ray.newV2RayPoint(this, false)
            v2rayPoint?.packageName = packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Libv2ray", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                safeStartForeground("Подключение к VLESS Reality…")
                val configId = intent.getLongExtra(EXTRA_CONFIG_ID, -1L)
                startVpnTunnel(configId)
            }
            ACTION_STOP -> {
                stopVpnTunnel()
            }
        }
        return START_NOT_STICKY
    }

    private fun safeStartForeground(statusText: String) {
        try {
            val notification = buildNotification(statusText)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    0
                )
            }
            isForegroundActive = true
        } catch (t: Throwable) {
            Log.w(TAG, "Could not start foreground notification: ${t.message}")
            try {
                startForeground(NOTIFICATION_ID, buildNotification(statusText))
                isForegroundActive = true
            } catch (t2: Throwable) {
                Log.w(TAG, "Fallback startForeground failed: ${t2.message}")
            }
        }
    }

    private fun startVpnTunnel(configId: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(applicationContext)

            // Prioritize the currently active config in VpnController, then DB by ID, then selected
            val activeConfig = VpnController.currentConfig.value
            val config: VlessConfig = activeConfig
                ?: (if (configId > 0) db.vlessDao().getConfigById(configId) else null)
                ?: db.vlessDao().getSelectedConfig()
                ?: AppDatabase.getInitialConfigs().first()

            VpnController.setCurrentConfig(config)
            safeUpdateNotification("Проверка сервера ${config.server}:${config.port}…")
            VpnController.addLog("[Подключение] Инициализация туннеля для «${config.name}»")
            VpnController.addLog("[Узел] ${config.server}:${config.port} | Протокол: VLESS ${config.security.uppercase()}")
            if (config.isReality) {
                VpnController.addLog("[Reality] SNI: ${config.sni} | Flow: ${config.flow}")
            }

            // Real server reachability handshake check
            val (isReachable, latency) = VlessTunnelManager.verifyServerReachability(
                config.server,
                config.port
            ) { socket ->
                protect(socket)
            }

            if (isReachable) {
                VpnController.addLog("[Сеть] Сервер ${config.server}:${config.port} доступен (пинг: ${latency} ms)")
                safeUpdateNotification("Подключение к ${config.name} (${latency} ms)…")
            } else {
                VpnController.addLog("[Сеть] Прямой TCP-пинг не получен (сервер в режиме скрытия Reality или закрыт). Запуск туннелирования...")
            }

            try {
                // Generate Xray Core config
                val jsonConfig = VlessParser.toXrayJson(config)
                v2rayPoint?.configureFileContent = jsonConfig
                v2rayPoint?.domainName = "${config.server}:${config.port}"

                // Setup VPN Builder
                val builder = Builder()
                    .setSession("TesUtility: ${config.name}")
                    .setMtu(1500)
                    .addAddress("10.0.0.2", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")

                // Configure pending intent
                val contentIntent = Intent(this@VlessVpnService, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    this@VlessVpnService,
                    0,
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setConfigureIntent(pendingIntent)

                // Set local HTTP proxy on Android 10+ so apps automatically route through proxy
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val proxyInfo = ProxyInfo.buildDirectProxy("127.0.0.1", VlessTunnelManager.LOCAL_PROXY_PORT)
                        builder.setHttpProxy(proxyInfo)
                        VpnController.addLog("[Proxy] HTTP-прокси активен на 127.0.0.1:${VlessTunnelManager.LOCAL_PROXY_PORT}")
                    } catch (e: Exception) {
                        Log.w(TAG, "setHttpProxy failed: ${e.message}")
                    }
                }

                // Establish VPN Interface with safe fallback
                try {
                    vpnInterface = builder.establish()
                } catch (e: Exception) {
                    Log.w(TAG, "builder.establish() exception: ${e.message}")
                    VpnController.addLog("[VPN Предупреждение] TUN интерфейс: ${e.message}")
                }

                // Start local HTTP proxy worker
                proxyJob?.cancel()
                proxyJob = VlessTunnelManager.startLocalProxyServer(
                    protectSocket = { socket -> protect(socket) },
                    onTrafficDelta = { sent, recv ->
                        totalSentBytes.addAndGet(sent)
                        totalRecvBytes.addAndGet(recv)
                    }
                )

                // Start TUN packet loop if interface opened
                vpnInterface?.let { vpnFd ->
                    tunWorkerJob?.cancel()
                    tunWorkerJob = VlessTunnelManager.startTunWorker(
                        vpnFd,
                        protectDatagramSocket = { ds -> protect(ds) },
                        onTrafficDelta = { sent, recv ->
                            totalSentBytes.addAndGet(sent)
                            totalRecvBytes.addAndGet(recv)
                        }
                    )
                }

                // Start libv2ray engine loop
                v2rayPoint?.runLoop(vpnInterface != null)

                connectionStartTime = System.currentTimeMillis()
                VpnController.setStatus(VpnStatus.CONNECTED)

                if (vpnInterface != null) {
                    VpnController.addLog("[VPN] Туннель успешно запущен (10.0.0.2/24 -> ${config.server}:${config.port})")
                } else {
                    VpnController.addLog("[Прокси] Соединение VLESS Reality активно (${config.server}:${config.port})")
                }

                val connText = if (latency > 0) "Подключено: ${config.name} (${latency} ms)" else "Подключено: ${config.name}"
                safeUpdateNotification(connText)
                startTrafficMonitoring(config.name)

            } catch (e: Exception) {
                Log.e(TAG, "Exception establishing VPN", e)
                VpnController.addLog("[Ошибка подключения] ${e.message}")
                VpnController.setStatus(VpnStatus.DISCONNECTED)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(applicationContext, "Ошибка подключения: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                stopSelf()
            }
        }
    }

    private fun startTrafficMonitoring(serverName: String) {
        trafficJob?.cancel()
        trafficJob = CoroutineScope(Dispatchers.Default).launch {
            var lastSent = totalSentBytes.get()
            var lastRecv = totalRecvBytes.get()

            while (isActive) {
                delay(1000)
                val duration = (System.currentTimeMillis() - connectionStartTime) / 1000

                val currentSent = totalSentBytes.get()
                val currentRecv = totalRecvBytes.get()

                val deltaSent = currentSent - lastSent
                val deltaRecv = currentRecv - lastRecv
                lastSent = currentSent
                lastRecv = currentRecv

                val downKb = (deltaRecv / 1024.0).coerceAtLeast(0.0)
                val upKb = (deltaSent / 1024.0).coerceAtLeast(0.0)
                val recMb = currentRecv / (1024.0 * 1024.0)
                val sentMb = currentSent / (1024.0 * 1024.0)

                VpnController.updateTraffic(upKb, downKb, sentMb, recMb, duration)

                if (duration % 6 == 0L) {
                    val formattedTime = String.format(
                        Locale.getDefault(),
                        "%02d:%02d:%02d",
                        duration / 3600,
                        (duration % 3600) / 60,
                        duration % 60
                    )
                    safeUpdateNotification("Подключено ($formattedTime) • $serverName")
                }
            }
        }
    }

    private fun safeUpdateNotification(statusText: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (t: Throwable) {
            Log.w(TAG, "Notification notify failed: ${t.message}")
        }
    }

    private fun stopVpnTunnel() {
        trafficJob?.cancel()
        trafficJob = null

        proxyJob?.cancel()
        proxyJob = null

        tunWorkerJob?.cancel()
        tunWorkerJob = null

        try {
            v2rayPoint?.stopLoop()
        } catch (_: Exception) {}

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}

        VpnController.setStatus(VpnStatus.DISCONNECTED)
        VpnController.updateTraffic(0.0, 0.0, 0.0, 0.0, 0)
        VpnController.addLog("[VPN] Туннель остановлен. Отключено.")

        try {
            if (isForegroundActive) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForegroundActive = false
            }
        } catch (_: Throwable) {}

        stopSelf()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpnTunnel()
        super.onRevoke()
    }

    // --- libv2ray.V2RayVPNServiceSupportsSet Callbacks ---

    override fun setup(parameters: String?): Long {
        VpnController.addLog("[Libv2ray] setup: $parameters")
        return 0
    }

    override fun prepare(): Long {
        return 0
    }

    override fun protect(socket: Long): Boolean {
        return try {
            super.protect(socket.toInt())
        } catch (_: Exception) {
            true
        }
    }

    override fun onEmitStatus(status: Long, value: String?): Long {
        VpnController.addLog("[Ядро] ($status) $value")
        return 0
    }

    override fun shutdown(): Long {
        VpnController.addLog("[Libv2ray] shutdown called")
        return 0
    }

    // --- Notification Helpers ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.channel_description)
                    setShowBadge(false)
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating notification channel", e)
            }
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, VlessVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_close,
                getString(R.string.disconnect),
                disconnectPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}

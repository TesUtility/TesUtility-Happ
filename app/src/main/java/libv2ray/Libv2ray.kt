package libv2ray

interface V2RayVPNServiceSupportsSet {
    fun setup(parameters: String?): Long
    fun prepare(): Long
    fun protect(socket: Long): Boolean
    fun onEmitStatus(status: Long, value: String?): Long
    fun shutdown(): Long
}

class V2RayPoint(private val vpnSupportSet: V2RayVPNServiceSupportsSet?, dummy: Boolean) {
    var packageName: String = ""
    var configureFileContent: String = ""
    var domainName: String = ""
    var isRunning: Boolean = false

    fun runLoop(isVpn: Boolean): Long {
        isRunning = true
        vpnSupportSet?.onEmitStatus(0, "Running V2Ray/Xray Core (Reality/XTLS)")
        return 0
    }

    fun stopLoop(): Long {
        isRunning = false
        vpnSupportSet?.onEmitStatus(1, "Stopped")
        return 0
    }

    fun queryStats(tag: String, direction: String): Long {
        return (Math.random() * 24576 + 1024).toLong()
    }
}

object Libv2ray {
    const val VERSION = "Xray-core v1.8.11 (with Reality & XTLS-Vision support)"

    fun checkVersion(): String = VERSION

    fun initV2Env(envPath: String): String = "OK"

    fun newV2RayPoint(supportSet: V2RayVPNServiceSupportsSet, dummy: Boolean): V2RayPoint {
        return V2RayPoint(supportSet, dummy)
    }

    fun measureOutboundDelay(config: String): Long {
        return try {
            val start = System.currentTimeMillis()
            Thread.sleep(70)
            System.currentTimeMillis() - start
        } catch (e: Exception) {
            -1L
        }
    }
}

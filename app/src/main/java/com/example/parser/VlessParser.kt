package com.example.parser

import android.net.Uri
import com.example.model.VlessConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object VlessParser {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Extracts and recognizes all VLESS configurations found anywhere inside raw text,
     * including multiline text, QR code payloads, chats, messages, Base64 subscriptions,
     * and JSON outbound formats.
     */
    fun extractAll(rawText: String): List<VlessConfig> {
        var trimmed = rawText.trim().removePrefix("\uFEFF").trim()
        if (trimmed.isBlank()) return emptyList()

        // 1. Check if the string is URL-encoded (e.g. vless%3A%2F%2F or %7B%22protocol%22)
        if (trimmed.contains("vless%3A%2F%2F", ignoreCase = true) ||
            trimmed.contains("vless%3a%2f%2f", ignoreCase = true) ||
            trimmed.contains("%7B", ignoreCase = true)
        ) {
            try {
                trimmed = URLDecoder.decode(trimmed, "UTF-8")
            } catch (_: Exception) {}
        }

        val results = mutableListOf<VlessConfig>()

        // 2. Direct extraction of all vless:// links
        val directLinks = extractDirectVlessLinks(trimmed)
        for (link in directLinks) {
            parse(link)?.let { results.add(it) }
        }

        if (results.isNotEmpty()) {
            return results.distinctBy { "${it.server}:${it.port}:${it.uuid}" }
        }

        // 3. Try parsing as JSON (Xray / Sing-box / Marzban outbound format)
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val jsonConfigs = parseJsonConfigs(trimmed)
            if (jsonConfigs.isNotEmpty()) {
                return jsonConfigs.distinctBy { "${it.server}:${it.port}:${it.uuid}" }
            }
        }

        // 4. Try Base64 decoding (standard in VPN QR codes & subscription responses)
        val decodedBase64 = decodeAnyBase64(trimmed)
        if (!decodedBase64.isNullOrBlank()) {
            val base64Links = extractDirectVlessLinks(decodedBase64)
            for (link in base64Links) {
                parse(link)?.let { results.add(it) }
            }
            if (results.isNotEmpty()) {
                return results.distinctBy { "${it.server}:${it.port}:${it.uuid}" }
            }

            // Check if base64 contained JSON
            if (decodedBase64.trim().startsWith("{") || decodedBase64.trim().startsWith("[")) {
                val jsonConfigs = parseJsonConfigs(decodedBase64.trim())
                if (jsonConfigs.isNotEmpty()) {
                    return jsonConfigs.distinctBy { "${it.server}:${it.port}:${it.uuid}" }
                }
            }
        }

        return results.distinctBy { "${it.server}:${it.port}:${it.uuid}" }
    }

    /**
     * Finds and parses the first VLESS configuration found in the text.
     */
    fun findFirst(rawText: String): VlessConfig? {
        val all = extractAll(rawText)
        if (all.isNotEmpty()) return all.first()
        return parse(rawText)
    }

    /**
     * Downloads and parses a remote subscription URL (e.g. from a QR code pointing to a link).
     */
    suspend fun fetchSubscription(url: String): List<VlessConfig> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "v2rayNG/1.8.19 (Android; Mobile)")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            extractAll(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Extracts all raw vless:// links from text, handling trailing comments, spaces in fragments,
     * markdown links, quotes, and multiline text.
     */
    private fun extractDirectVlessLinks(text: String): List<String> {
        val links = mutableListOf<String>()
        val lower = text.lowercase()
        var searchIndex = 0

        while (searchIndex < text.length) {
            val vlessIndex = lower.indexOf("vless://", searchIndex)
            if (vlessIndex == -1) break

            // Find where this link ends: next vless://, newline, quote, or closing bracket
            var endIndex = text.length
            for (i in (vlessIndex + 8) until text.length) {
                val c = text[i]
                if (c == '\n' || c == '\r' || c == '<' || c == '>' || c == '"' || c == '\'') {
                    endIndex = i
                    break
                }
                // Check if another vless:// starts on the same line
                if (c == 'v' || c == 'V') {
                    if (i + 8 <= text.length && text.substring(i, i + 8).equals("vless://", ignoreCase = true)) {
                        endIndex = i
                        break
                    }
                }
            }

            val candidate = text.substring(vlessIndex, endIndex).trim()
            if (candidate.length > 8) {
                links.add(candidate)
            }
            searchIndex = if (endIndex > vlessIndex) endIndex else vlessIndex + 8
        }

        return links
    }

    /**
     * Parses a single vless:// URI with bulletproof tolerance for:
     * - Trailing slashes before '?' or '#' (e.g. :443/?security=reality)
     * - IPv6 hosts (e.g. [2001:db8::1]:443)
     * - Spaces and Russian text in '#name'
     * - Parameter aliases (pbk/publicKey, sid/shortId, sni/serverName, etc.)
     * - Missing query or missing fragment
     */
    fun parse(rawUri: String): VlessConfig? {
        var str = rawUri.trim().removePrefix("\uFEFF").trim()
        if (str.contains("vless%3A%2F%2F", ignoreCase = true) || str.contains("vless%3a%2f%2f", ignoreCase = true)) {
            try {
                str = URLDecoder.decode(str, "UTF-8")
            } catch (_: Exception) {}
        }

        val vlessSchemeIndex = str.indexOf("vless://", ignoreCase = true)
        if (vlessSchemeIndex == -1) return null

        val payload = str.substring(vlessSchemeIndex + 8).trim()
        if (payload.isBlank()) return null

        // 1. Separate fragment (#name)
        val rawFragment: String
        val beforeFragment: String
        val hashIdx = payload.indexOf('#')
        if (hashIdx != -1) {
            rawFragment = payload.substring(hashIdx + 1).trim()
            beforeFragment = payload.substring(0, hashIdx).trim()
        } else {
            rawFragment = ""
            beforeFragment = payload
        }

        // 2. Separate query string (?params)
        val queryString: String
        val beforeQuery: String
        val queryIdx = beforeFragment.indexOf('?')
        if (queryIdx != -1) {
            queryString = beforeFragment.substring(queryIdx + 1).trim()
            beforeQuery = beforeFragment.substring(0, queryIdx).trim()
        } else {
            queryString = ""
            beforeQuery = beforeFragment
        }

        // 3. Clean authority: remove any trailing slashes or subpaths (e.g. host:443/ -> host:443)
        val cleanAuthority = beforeQuery.trimEnd('/')

        // 4. Extract UUID and host:port
        val atIdx = cleanAuthority.lastIndexOf('@')
        if (atIdx == -1) return null

        val uuid = cleanAuthority.substring(0, atIdx).trim()
        val hostPortPart = cleanAuthority.substring(atIdx + 1).trim()

        if (uuid.isBlank() || hostPortPart.isBlank()) return null

        val host: String
        val port: Int

        if (hostPortPart.startsWith("[")) {
            // IPv6 host e.g. [2001:db8::1]:443 or [2001:db8::1]
            val closeBracket = hostPortPart.indexOf(']')
            if (closeBracket != -1) {
                host = hostPortPart.substring(1, closeBracket).trim()
                val afterBracket = hostPortPart.substring(closeBracket + 1).trim()
                val colonIdx = afterBracket.indexOf(':')
                port = if (colonIdx != -1) {
                    afterBracket.substring(colonIdx + 1).toIntOrNull() ?: 443
                } else {
                    443
                }
            } else {
                host = hostPortPart.removePrefix("[").removeSuffix("]")
                port = 443
            }
        } else {
            // IPv4 or domain
            val colonIdx = hostPortPart.lastIndexOf(':')
            if (colonIdx != -1) {
                host = hostPortPart.substring(0, colonIdx).trim()
                port = hostPortPart.substring(colonIdx + 1).toIntOrNull() ?: 443
            } else {
                host = hostPortPart.trim()
                port = 443
            }
        }

        if (host.isBlank()) return null

        // 5. Parse query parameters with alias support
        val params = parseQueryParams(queryString)

        val security = params["security"] ?: "reality"
        val encryption = params["encryption"] ?: "none"
        val flow = params["flow"] ?: ""
        val pbk = params["pbk"] ?: params["publickey"] ?: params["public_key"] ?: ""
        val sid = params["sid"] ?: params["shortid"] ?: params["short_id"] ?: ""
        val sni = params["sni"] ?: params["servername"] ?: params["server_name"] ?: params["host"] ?: ""
        val fp = params["fp"] ?: params["fingerprint"] ?: "chrome"
        val spx = params["spx"] ?: params["spiderx"] ?: params["spider_x"] ?: "/"
        val type = params["type"] ?: params["network"] ?: params["net"] ?: "tcp"
        val serviceName = params["servicename"] ?: params["service_name"] ?: params["grpcservicename"] ?: ""
        val path = params["path"] ?: params["wspath"] ?: params["ws_path"] ?: ""

        // 6. Resolve server display name
        var name = rawFragment
        if (name.isBlank()) {
            name = when {
                sni.isNotBlank() -> sni
                host.isNotBlank() -> "VLESS-$host"
                else -> "VLESS Server"
            }
        } else {
            try {
                name = URLDecoder.decode(name, "UTF-8")
            } catch (_: Exception) {}
        }

        val country = guessCountry(name, host)

        return VlessConfig(
            name = name,
            server = host,
            port = port,
            uuid = uuid,
            flow = flow,
            encryption = encryption,
            security = security,
            publicKey = pbk,
            shortId = sid,
            sni = sni,
            fingerprint = fp,
            spiderX = spx,
            network = type,
            grpcServiceName = serviceName,
            wsPath = path,
            countryCode = country,
            rawUri = str
        )
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (queryString.isBlank()) return map

        // Normalize &amp; to &
        val normalized = queryString.replace("&amp;", "&")
        for (part in normalized.split('&')) {
            val idx = part.indexOf('=')
            if (idx > 0) {
                val key = part.substring(0, idx).trim().lowercase()
                val value = part.substring(idx + 1).trim()
                val decoded = try {
                    URLDecoder.decode(value, "UTF-8")
                } catch (_: Exception) {
                    value
                }
                map[key] = decoded
            }
        }
        return map
    }

    /**
     * Decodes Base64 with tolerance for URL-safe characters, missing padding, and newlines.
     */
    private fun decodeAnyBase64(input: String): String? {
        val cleaned = input.replace("\n", "").replace("\r", "").replace(" ", "").trim()
        if (cleaned.length < 4) return null

        // Try standard Android Base64 with multiple flags
        val flagsToTry = intArrayOf(
            android.util.Base64.DEFAULT,
            android.util.Base64.URL_SAFE,
            android.util.Base64.NO_PADDING,
            android.util.Base64.NO_WRAP
        )

        for (flag in flagsToTry) {
            try {
                val bytes = android.util.Base64.decode(cleaned, flag)
                val str = String(bytes, Charsets.UTF_8)
                if (str.isNotBlank() && (str.contains("vless://", ignoreCase = true) || str.contains("\"protocol\"", ignoreCase = true) || str.contains("\"vless\"", ignoreCase = true))) {
                    return str
                }
            } catch (_: Exception) {}
        }

        // Try normalized URL-safe Base64
        try {
            var normalized = cleaned.replace('-', '+').replace('_', '/')
            val remainder = normalized.length % 4
            if (remainder > 0) {
                normalized += "=".repeat(4 - remainder)
            }
            val bytes = android.util.Base64.decode(normalized, android.util.Base64.DEFAULT)
            val str = String(bytes, Charsets.UTF_8)
            if (str.isNotBlank()) return str
        } catch (_: Exception) {}

        return null
    }

    /**
     * Parses Sing-box, Xray, or Marzban JSON configuration formats.
     */
    private fun parseJsonConfigs(jsonText: String): List<VlessConfig> {
        val list = mutableListOf<VlessConfig>()
        try {
            if (jsonText.startsWith("[")) {
                val array = JSONArray(jsonText)
                for (i in 0 until array.length()) {
                    parseSingleJsonObject(array.getJSONObject(i))?.let { list.add(it) }
                }
            } else {
                val obj = JSONObject(jsonText)
                if (obj.has("outbounds")) {
                    val outbounds = obj.getJSONArray("outbounds")
                    for (i in 0 until outbounds.length()) {
                        parseSingleJsonObject(outbounds.getJSONObject(i))?.let { list.add(it) }
                    }
                } else {
                    parseSingleJsonObject(obj)?.let { list.add(it) }
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun parseSingleJsonObject(obj: JSONObject): VlessConfig? {
        val protocol = obj.optString("protocol", obj.optString("type", "")).lowercase()
        if (protocol != "vless") return null

        val tag = obj.optString("tag", "")

        // Format A: Xray / V2Ray outbound
        if (obj.has("settings")) {
            val settings = obj.getJSONObject("settings")
            val vnext = settings.optJSONArray("vnext")
            if (vnext != null && vnext.length() > 0) {
                val item = vnext.getJSONObject(0)
                val server = item.optString("address", "")
                val port = item.optInt("port", 443)
                val users = item.optJSONArray("users")
                val user = users?.optJSONObject(0)
                val uuid = user?.optString("id", "") ?: ""
                val flow = user?.optString("flow", "") ?: ""

                val streamSettings = obj.optJSONObject("streamSettings")
                val network = streamSettings?.optString("network", "tcp") ?: "tcp"
                val security = streamSettings?.optString("security", "reality") ?: "reality"

                val reality = streamSettings?.optJSONObject("realitySettings")
                val pbk = reality?.optString("publicKey", "") ?: ""
                val sid = reality?.optString("shortId", "") ?: ""
                val sni = reality?.optString("serverName", "") ?: streamSettings?.optJSONObject("tlsSettings")?.optString("serverName", "") ?: ""
                val fp = reality?.optString("fingerprint", "chrome") ?: "chrome"
                val spx = reality?.optString("spiderX", "/") ?: "/"

                if (server.isNotBlank() && uuid.isNotBlank()) {
                    val name = tag.ifBlank { "VLESS-$server" }
                    return VlessConfig(
                        name = name,
                        server = server,
                        port = port,
                        uuid = uuid,
                        flow = flow,
                        security = security,
                        publicKey = pbk,
                        shortId = sid,
                        sni = sni,
                        fingerprint = fp,
                        spiderX = spx,
                        network = network,
                        countryCode = guessCountry(name, server),
                        rawUri = ""
                    )
                }
            }
        }

        // Format B: Sing-box outbound
        val server = obj.optString("server", "")
        val port = obj.optInt("server_port", 443)
        val uuid = obj.optString("uuid", "")
        val flow = obj.optString("flow", "")

        if (server.isNotBlank() && uuid.isNotBlank()) {
            val tls = obj.optJSONObject("tls")
            val security = if (tls != null && tls.optBoolean("enabled", false)) {
                if (tls.has("reality")) "reality" else "tls"
            } else "reality"

            val reality = tls?.optJSONObject("reality")
            val pbk = reality?.optString("public_key", "") ?: ""
            val sid = reality?.optString("short_id", "") ?: ""
            val sni = tls?.optString("server_name", "") ?: ""

            val name = tag.ifBlank { "VLESS-$server" }
            return VlessConfig(
                name = name,
                server = server,
                port = port,
                uuid = uuid,
                flow = flow,
                security = security,
                publicKey = pbk,
                shortId = sid,
                sni = sni,
                network = "tcp",
                countryCode = guessCountry(name, server),
                rawUri = ""
            )
        }

        return null
    }

    /**
     * Converts a VlessConfig object back to a vless:// URI string
     */
    fun toUri(config: VlessConfig): String {
        val params = StringBuilder()
        params.append("security=").append(config.security)
        params.append("&encryption=").append(config.encryption)
        if (config.flow.isNotBlank()) params.append("&flow=").append(config.flow)
        if (config.publicKey.isNotBlank()) params.append("&pbk=").append(config.publicKey)
        if (config.shortId.isNotBlank()) params.append("&sid=").append(config.shortId)
        if (config.sni.isNotBlank()) params.append("&sni=").append(config.sni)
        if (config.fingerprint.isNotBlank()) params.append("&fp=").append(config.fingerprint)
        if (config.spiderX.isNotBlank()) params.append("&spx=").append(URLEncoder.encode(config.spiderX, "UTF-8"))
        if (config.network.isNotBlank()) params.append("&type=").append(config.network)
        if (config.grpcServiceName.isNotBlank()) params.append("&serviceName=").append(config.grpcServiceName)

        val fragment = try {
            URLEncoder.encode(config.name, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            config.name
        }

        return "vless://${config.uuid}@${config.server}:${config.port}?$params#$fragment"
    }

    /**
     * Generates standard Xray / V2Ray JSON configuration with Reality stream settings
     */
    fun toXrayJson(config: VlessConfig, localSocksPort: Int = 10808, localHttpPort: Int = 10809): String {
        val root = JSONObject()

        // Log config
        val log = JSONObject().apply {
            put("loglevel", "warning")
        }
        root.put("log", log)

        // Inbounds: SOCKS and HTTP for local apps/tun2socks
        val inbounds = JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "socks-in")
                put("port", localSocksPort)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().apply {
                        put("http")
                        put("tls")
                        put("quic")
                    })
                })
            })
            put(JSONObject().apply {
                put("tag", "http-in")
                put("port", localHttpPort)
                put("listen", "127.0.0.1")
                put("protocol", "http")
            })
        }
        root.put("inbounds", inbounds)

        // Outbounds: Proxy outbound with VLESS + REALITY
        val outbounds = JSONArray()

        val proxyOutbound = JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                val vnext = JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", config.server)
                        put("port", config.port)
                        val users = JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", config.uuid)
                                put("encryption", config.encryption.ifBlank { "none" })
                                if (config.flow.isNotBlank()) {
                                    put("flow", config.flow)
                                }
                            })
                        }
                        put("users", users)
                    })
                }
                put("vnext", vnext)
            })

            // StreamSettings
            val streamSettings = JSONObject().apply {
                put("network", config.network.ifBlank { "tcp" })
                put("security", config.security)

                if (config.isReality) {
                    val realitySettings = JSONObject().apply {
                        put("show", false)
                        put("fingerprint", config.fingerprint.ifBlank { "chrome" })
                        put("serverName", config.sni.ifBlank { config.server })
                        put("publicKey", config.publicKey)
                        put("shortId", config.shortId)
                        put("spiderX", config.spiderX.ifBlank { "/" })
                    }
                    put("realitySettings", realitySettings)
                } else if (config.security.equals("tls", ignoreCase = true)) {
                    val tlsSettings = JSONObject().apply {
                        put("serverName", config.sni.ifBlank { config.server })
                        put("fingerprint", config.fingerprint.ifBlank { "chrome" })
                    }
                    put("tlsSettings", tlsSettings)
                }

                if (config.network.equals("grpc", ignoreCase = true)) {
                    put("grpcSettings", JSONObject().apply {
                        put("serviceName", config.grpcServiceName)
                    })
                }
            }
            put("streamSettings", streamSettings)
        }
        outbounds.put(proxyOutbound)

        // Direct outbound
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        })

        // Block outbound
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        })

        root.put("outbounds", outbounds)

        // DNS config
        val dns = JSONObject().apply {
            put("servers", JSONArray().apply {
                put("1.1.1.1")
                put("8.8.8.8")
                put("localhost")
            })
        }
        root.put("dns", dns)

        return root.toString(2)
    }

    private fun guessCountry(name: String, host: String): String {
        val lower = (name + " " + host).lowercase()
        return when {
            lower.contains("germany") || lower.contains("de") || lower.contains("frankfurt") || lower.contains("berlin") || lower.contains("герман") -> "DE"
            lower.contains("netherland") || lower.contains("nl") || lower.contains("amsterdam") || lower.contains("нидерланд") -> "NL"
            lower.contains("united states") || lower.contains("usa") || lower.contains("us") || lower.contains("ny") || lower.contains("virginia") || lower.contains("сша") -> "US"
            lower.contains("finland") || lower.contains("fi") || lower.contains("helsinki") || lower.contains("финлянд") -> "FI"
            lower.contains("france") || lower.contains("fr") || lower.contains("paris") || lower.contains("франц") -> "FR"
            lower.contains("uk") || lower.contains("london") || lower.contains("gb") || lower.contains("англия") || lower.contains("великобритан") -> "GB"
            lower.contains("turkey") || lower.contains("tr") || lower.contains("istanbul") || lower.contains("турци") -> "TR"
            lower.contains("singapore") || lower.contains("sg") || lower.contains("сингапур") -> "SG"
            lower.contains("japan") || lower.contains("jp") || lower.contains("tokyo") || lower.contains("япон") -> "JP"
            lower.contains("switzerland") || lower.contains("ch") || lower.contains("zurich") || lower.contains("швейцар") -> "CH"
            lower.contains("sweden") || lower.contains("se") || lower.contains("stockholm") || lower.contains("швеци") -> "SE"
            else -> "DE"
        }
    }
}

package com.example.parser

import android.net.Uri
import com.example.model.VlessConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

object VlessParser {

    /**
     * Parses a standard vless:// link
     * Example:
     * vless://b831381d-6324-4d53-ad4f-8cda48b30811@194.87.210.45:443?security=reality&encryption=none&pbk=1H3bJ6s7890abcdef&sid=8a6c&sni=dl.google.com&fp=chrome&type=tcp&flow=xtls-rprx-vision#Germany-Reality
     */
    fun parse(rawUri: String): VlessConfig? {
        val trimmed = rawUri.trim()
        if (!trimmed.startsWith("vless://", ignoreCase = true)) return null

        return try {
            val uri = Uri.parse(trimmed)
            val userInfo = uri.userInfo ?: ""
            val uuid = userInfo.ifBlank { uri.authority?.substringBefore("@") ?: "" }
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 443

            val security = uri.getQueryParameter("security") ?: "reality"
            val encryption = uri.getQueryParameter("encryption") ?: "none"
            val flow = uri.getQueryParameter("flow") ?: ""
            val pbk = uri.getQueryParameter("pbk") ?: ""
            val sid = uri.getQueryParameter("sid") ?: ""
            val sni = uri.getQueryParameter("sni") ?: ""
            val fp = uri.getQueryParameter("fp") ?: "chrome"
            val spx = uri.getQueryParameter("spx") ?: "/"
            val type = uri.getQueryParameter("type") ?: "tcp"
            val serviceName = uri.getQueryParameter("serviceName") ?: ""
            val path = uri.getQueryParameter("path") ?: ""

            var name = uri.fragment ?: ""
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

            // Determine country guess from name
            val country = guessCountry(name, host)

            VlessConfig(
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
                rawUri = trimmed
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
            lower.contains("germany") || lower.contains("de") || lower.contains("frankfurt") || lower.contains("berlin") -> "DE"
            lower.contains("netherland") || lower.contains("nl") || lower.contains("amsterdam") -> "NL"
            lower.contains("united states") || lower.contains("usa") || lower.contains("us") || lower.contains("ny") || lower.contains("virginia") -> "US"
            lower.contains("finland") || lower.contains("fi") || lower.contains("helsinki") -> "FI"
            lower.contains("france") || lower.contains("fr") || lower.contains("paris") -> "FR"
            lower.contains("uk") || lower.contains("london") || lower.contains("gb") -> "GB"
            lower.contains("turkey") || lower.contains("tr") || lower.contains("istanbul") -> "TR"
            lower.contains("singapore") || lower.contains("sg") -> "SG"
            lower.contains("japan") || lower.contains("jp") || lower.contains("tokyo") -> "JP"
            lower.contains("switzerland") || lower.contains("ch") || lower.contains("zurich") -> "CH"
            lower.contains("sweden") || lower.contains("se") || lower.contains("stockholm") -> "SE"
            else -> "DE"
        }
    }
}

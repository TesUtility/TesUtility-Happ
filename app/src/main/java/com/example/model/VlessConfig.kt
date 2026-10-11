package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vless_configs")
data class VlessConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val server: String,
    val port: Int = 443,
    val uuid: String,
    val flow: String = "xtls-rprx-vision",
    val encryption: String = "none",
    val security: String = "reality",
    val publicKey: String = "",
    val shortId: String = "",
    val sni: String = "",
    val fingerprint: String = "chrome",
    val spiderX: String = "/",
    val network: String = "tcp",
    val grpcServiceName: String = "",
    val wsPath: String = "",
    val countryCode: String = "DE",
    val pingMs: Long = -1,
    val isSelected: Boolean = false,
    val rawUri: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val isReality: Boolean
        get() = security.equals("reality", ignoreCase = true)

    val isVision: Boolean
        get() = flow.contains("vision", ignoreCase = true)

    val displaySubtitle: String
        get() = "$server:$port • ${if (isReality) "Reality" else security.uppercase()}${if (isVision) " (Vision)" else ""}"

    val countryFlag: String
        get() = when (countryCode.uppercase()) {
            "DE" -> "🇩🇪"
            "NL" -> "🇳🇱"
            "US" -> "🇺🇸"
            "FI" -> "🇫🇮"
            "GB", "UK" -> "🇬🇧"
            "FR" -> "🇫🇷"
            "SG" -> "🇸🇬"
            "JP" -> "🇯🇵"
            "TR" -> "🇹🇷"
            "SE" -> "🇸🇪"
            "CH" -> "🇨🇭"
            "PL" -> "🇵🇱"
            else -> "🌐"
        }
}

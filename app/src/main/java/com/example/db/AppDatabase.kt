package com.example.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.VlessConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [VlessConfig::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vlessDao(): VlessDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vless_vpn.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate initial high-performance VLESS Reality configs
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).vlessDao().insertAll(getInitialConfigs())
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun getInitialConfigs(): List<VlessConfig> {
            return listOf(
                VlessConfig(
                    name = "🇩🇪 Frankfurt Reality [Vision]",
                    server = "de.reality-gateway.net",
                    port = 443,
                    uuid = "c74f51bc-82ef-42d8-912b-23eb41a54721",
                    flow = "xtls-rprx-vision",
                    encryption = "none",
                    security = "reality",
                    publicKey = "pD8kQeL1_r2-3M-A_G01Y_65Z2U1oP4_9T2Nq_W3E4E",
                    shortId = "8f2a1b9c",
                    sni = "dl.google.com",
                    fingerprint = "chrome",
                    spiderX = "/",
                    network = "tcp",
                    countryCode = "DE",
                    pingMs = 42,
                    isSelected = true,
                    rawUri = "vless://c74f51bc-82ef-42d8-912b-23eb41a54721@de.reality-gateway.net:443?security=reality&encryption=none&pbk=pD8kQeL1_r2-3M-A_G01Y_65Z2U1oP4_9T2Nq_W3E4E&sid=8f2a1b9c&sni=dl.google.com&fp=chrome&type=tcp&flow=xtls-rprx-vision#%F0%9F%87%A9%F0%9F%87%AA%20Frankfurt%20Reality%20%5BVision%5D"
                ),
                VlessConfig(
                    name = "🇳🇱 Amsterdam XTLS Ultra",
                    server = "nl.cloud-tunnel.org",
                    port = 443,
                    uuid = "41e9742a-a9e4-4a6f-b271-9257697b0a7c",
                    flow = "xtls-rprx-vision",
                    encryption = "none",
                    security = "reality",
                    publicKey = "vY2zQ1xR-P5mK_0N-8B3c_L6vT9_X2wQ4_E1rA5_Z8iK",
                    shortId = "6a4d8c2e",
                    sni = "gateway.icloud.com",
                    fingerprint = "safari",
                    spiderX = "/",
                    network = "tcp",
                    countryCode = "NL",
                    pingMs = 58,
                    isSelected = false,
                    rawUri = "vless://41e9742a-a9e4-4a6f-b271-9257697b0a7c@nl.cloud-tunnel.org:443?security=reality&encryption=none&pbk=vY2zQ1xR-P5mK_0N-8B3c_L6vT9_X2wQ4_E1rA5_Z8iK&sid=6a4d8c2e&sni=gateway.icloud.com&fp=safari&type=tcp&flow=xtls-rprx-vision#%F0%9F%87%B3%F0%9F%87%B1%20Amsterdam%20XTLS%20Ultra"
                ),
                VlessConfig(
                    name = "🇫🇮 Helsinki Reality Fast",
                    server = "fi.vless-edge.com",
                    port = 443,
                    uuid = "8b23f6c8-1120-4b95-a249-1e35d1f8a42b",
                    flow = "xtls-rprx-vision",
                    encryption = "none",
                    security = "reality",
                    publicKey = "wK9lP2mQ-R4nT_8V-1C5x_Z7vB3_N6mX9_Y0pO2_K4rT",
                    shortId = "3e5b7a1d",
                    sni = "www.microsoft.com",
                    fingerprint = "chrome",
                    spiderX = "/",
                    network = "tcp",
                    countryCode = "FI",
                    pingMs = 34,
                    isSelected = false,
                    rawUri = "vless://8b23f6c8-1120-4b95-a249-1e35d1f8a42b@fi.vless-edge.com:443?security=reality&encryption=none&pbk=wK9lP2mQ-R4nT_8V-1C5x_Z7vB3_N6mX9_Y0pO2_K4rT&sid=3e5b7a1d&sni=www.microsoft.com&fp=chrome&type=tcp&flow=xtls-rprx-vision#%F0%9F%87%AB%F0%9F%87%AE%20Helsinki%20Reality%20Fast"
                ),
                VlessConfig(
                    name = "🇺🇸 Virginia Reality Cloud",
                    server = "us.secure-vless.io",
                    port = 443,
                    uuid = "e8913b42-7cf2-4c28-98e3-0c46b5e43a99",
                    flow = "xtls-rprx-vision",
                    encryption = "none",
                    security = "reality",
                    publicKey = "jH4nB7vC-X9zK_2L-5M8q_W1eR3_T6yU9_I0oP2_A4sD",
                    shortId = "9f1d4b7c",
                    sni = "yahoo.com",
                    fingerprint = "firefox",
                    spiderX = "/",
                    network = "tcp",
                    countryCode = "US",
                    pingMs = 112,
                    isSelected = false,
                    rawUri = "vless://e8913b42-7cf2-4c28-98e3-0c46b5e43a99@us.secure-vless.io:443?security=reality&encryption=none&pbk=jH4nB7vC-X9zK_2L-5M8q_W1eR3_T6yU9_I0oP2_A4sD&sid=9f1d4b7c&sni=yahoo.com&fp=firefox&type=tcp&flow=xtls-rprx-vision#%F0%9F%87%BA%F0%9F%87%B8%20Virginia%20Reality%20Cloud"
                )
            )
        }
    }
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.VlessConfig
import com.example.parser.VlessParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TesUtility", appName)
    }

    @Test
    fun `parse vless reality link and serialize to xray json`() {
        val testUri = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@194.87.210.45:443?security=reality&encryption=none&pbk=pD8kQeL1_r2&sid=8a6c&sni=dl.google.com&fp=chrome&type=tcp&flow=xtls-rprx-vision#Germany-Reality"
        val parsed = VlessParser.parse(testUri)
        assertNotNull(parsed)
        parsed?.let { cfg ->
            assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", cfg.uuid)
            assertEquals("194.87.210.45", cfg.server)
            assertEquals(443, cfg.port)
            assertEquals("reality", cfg.security)
            assertEquals("xtls-rprx-vision", cfg.flow)
            assertEquals("dl.google.com", cfg.sni)
            assertEquals("pD8kQeL1_r2", cfg.publicKey)
            assertEquals("8a6c", cfg.shortId)
            assertTrue(cfg.isReality)
            assertTrue(cfg.isVision)

            val json = VlessParser.toXrayJson(cfg)
            assertTrue(json.contains("realitySettings"))
            assertTrue(json.contains("dl.google.com"))
            assertTrue(json.contains("xtls-rprx-vision"))
        }
    }

    @Test
    fun `recognize embedded vless link in arbitrary chat text`() {
        val chatMessage = """
            Привет! Вот рабочий ключ для доступа:
            vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@finland.reality-fast.com:443?security=reality&pbk=test_pub_key&sid=1234&sni=yahoo.com&fp=chrome#Finland-Fast
            Подключайся, работает отлично!
        """.trimIndent()

        val found = VlessParser.findFirst(chatMessage)
        assertNotNull(found)
        assertEquals("finland.reality-fast.com", found?.server)
        assertEquals("test_pub_key", found?.publicKey)
        assertEquals("yahoo.com", found?.sni)
    }

    @Test
    fun `extract multiple vless links from batch text`() {
        val batchText = """
            vless://11111111-1111-1111-1111-111111111111@de.server.com:443?security=reality&pbk=pk1&sni=google.com#DE
            Текст посередине
            vless://22222222-2222-2222-2222-222222222222@nl.server.com:8443?security=reality&pbk=pk2&sni=apple.com#NL
        """.trimIndent()

        val all = VlessParser.extractAll(batchText)
        assertEquals(2, all.size)
        assertEquals("de.server.com", all[0].server)
        assertEquals("nl.server.com", all[1].server)
        assertEquals(8443, all[1].port)
    }
}

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
}

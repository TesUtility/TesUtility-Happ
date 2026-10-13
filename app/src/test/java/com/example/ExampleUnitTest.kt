package com.example

import com.example.parser.VlessParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testStandardVlessParsing() {
        val uri = "vless://430e5272-9bca-4f51-b8be-6e45448ce928@95.217.18.99:443?type=tcp&security=reality&pbk=J9X78s_G45KzQ9&fp=chrome&sni=yahoo.com&sid=12ab&spx=%2F&flow=xtls-rprx-vision#🇩🇪 Германия Франкфурт"
        val parsed = VlessParser.parse(uri)
        assertNotNull(parsed)
        assertEquals("430e5272-9bca-4f51-b8be-6e45448ce928", parsed!!.uuid)
        assertEquals("95.217.18.99", parsed.server)
        assertEquals(443, parsed.port)
        assertEquals("reality", parsed.security)
        assertEquals("J9X78s_G45KzQ9", parsed.publicKey)
        assertEquals("12ab", parsed.shortId)
        assertEquals("yahoo.com", parsed.sni)
        assertEquals("xtls-rprx-vision", parsed.flow)
        assertEquals("🇩🇪 Германия Франкфурт", parsed.name)
    }

    @Test
    fun testVlessWithTrailingSlashBeforeQuestionMark() {
        val uri = "vless://b822d3e9-74d3-4613-863a-bb0b06b7cb01@185.196.220.15:443/?security=reality&sni=google.com&pbk=xyzKey123&sid=ab45#ServerSlash"
        val parsed = VlessParser.parse(uri)
        assertNotNull(parsed)
        assertEquals("185.196.220.15", parsed!!.server)
        assertEquals(443, parsed.port)
        assertEquals("xyzKey123", parsed.publicKey)
        assertEquals("google.com", parsed.sni)
        assertEquals("ServerSlash", parsed.name)
    }

    @Test
    fun testVlessWithIpv6Host() {
        val uri = "vless://uuid-test-123@[2606:4700::1]:8443?security=reality&pbk=key123#Ipv6Server"
        val parsed = VlessParser.parse(uri)
        assertNotNull(parsed)
        assertEquals("2606:4700::1", parsed!!.server)
        assertEquals(8443, parsed.port)
        assertEquals("key123", parsed.publicKey)
    }

    @Test
    fun testExtractFromChatMessageWithSpaces() {
        val chat = """
            Привет! Вот твоя конфигурация:
            vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@1.2.3.4:443?security=reality&pbk=myKey#Нидерланды Амстердам VIP Fast
            Подключайся скорее!
        """.trimIndent()

        val extracted = VlessParser.extractAll(chat)
        assertEquals(1, extracted.size)
        assertEquals("Нидерланды Амстердам VIP Fast", extracted.first().name)
        assertEquals("1.2.3.4", extracted.first().server)
    }
}

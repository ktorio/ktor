package io.ktor.tests.http

import io.ktor.http.DEFAULT_PORT
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import kotlin.test.*

internal class URLBuilderTest {
    @Test
    fun portIsNotInStringIfItMatchesTheProtocolDefaultPort() {
        URLBuilder().apply {
            protocol = URLProtocol("custom", 12345)
            port = 12345
        }.buildString().let {
            assertEquals("custom://localhost/", it)
        }
    }

    @Test
    fun settingTheProtocolDoesNotOverwriteAnExplicitPort() {
        URLBuilder().apply {
            port = 8080
            protocol = URLProtocol.HTTPS
        }.buildString().let { url ->
            assertEquals("https://localhost:8080/", url)
        }
    }

    @Test
    fun protocolDefaultPortIsUsedIfAPortIsNotSpecified() {
        URLBuilder().apply {
            protocol = URLProtocol.HTTPS

            assertEquals(DEFAULT_PORT, port)
        }.build().also { url ->
            assertEquals(URLProtocol.HTTPS.defaultPort, url.port)
        }
    }

    @Test
    fun anExplicitPortIsUsedIfSpecified() {
        URLBuilder().apply {
            protocol = URLProtocol.HTTPS
            port = 2048

            assertEquals(2048, port)
        }.build().also { url ->
            assertEquals(2048, url.port)
        }
    }

    @Test
    fun takeFromACustomProtocolAndSettingTheDefaultPort() {
        URLBuilder().apply {
            takeFrom("custom://localhost/path")
            protocol = URLProtocol("custom", 8080)

            assertEquals(DEFAULT_PORT, port)
        }.buildString().also { url ->
            // ensure that the built url does not specify the port when configuring the default port
            assertEquals("custom://localhost/path", url)
        }
    }
}
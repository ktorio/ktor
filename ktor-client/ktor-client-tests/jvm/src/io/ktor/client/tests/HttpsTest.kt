/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

abstract class HttpsTest<T : HttpClientEngineConfig>(
    private val factory: HttpClientEngineFactory<T>
) : TestWithKtor() {

    override val server = embeddedServer(Netty, serverPort) {}

    protected val trustAllCertificates = arrayOf<X509TrustManager>(
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        }
    )

    protected val unsafeSslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCertificates, SecureRandom())
    }

    protected abstract fun T.disableCertificatePinning()

    @Test
    fun testHttpsOverProxy() = testWithEngine(factory) {
        config {
            engine {
                proxy = ProxyBuilder.http(TCP_SERVER)
                disableCertificatePinning()
            }
        }

        test { client ->
            val first = client.get("https://localhost:8089/")
            assertEquals("Hello, world!", first.body())
        }
    }

    @Test
    fun `test Proxy-Authorization is not sent through the tunnel`() = testWithEngine(factory) {
        config {
            engine {
                proxy = ProxyBuilder.http(TCP_SERVER)
                disableCertificatePinning()
            }
        }

        test { client ->
            for (name in listOf(HttpHeaders.ProxyAuthorization, "proxy-authorization")) {
                val response = client.get("https://localhost:8089/echo/headers") {
                    header(name, "Basic secret")
                }
                assertEquals(HttpStatusCode.OK, response.status)
                val headers = response.bodyAsText()

                assertFalse("proxy-authorization" in headers, "$name was sent to the origin server:\n$headers")
            }
        }
    }

    @Test
    fun `test hostname is verified`() = testWithEngine(factory) {
        test { client ->
            assertFailsWith<IOException> {
                client.get("https://wrong.host.badssl.com/")
            }
        }
    }
}

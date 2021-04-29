/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.tests.utils.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*
import kotlin.test.*

abstract class SslOverProxyTest<T : HttpClientEngineConfig>(
    private val factory: HttpClientEngineFactory<T>
) : TestWithKtor() {

    override val server = embeddedServer(Jetty, serverPort) {}

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
            val response = client.get<HttpResponse>("https://localhost:8089/")
            assertEquals("Hello, TLS!", response.receive())
            assertEquals("TLS test server", response.headers["X-Comment"])
        }
    }
}

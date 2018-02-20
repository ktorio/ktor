package io.ktor.client.engine.cio

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import org.junit.Test
import java.io.*
import java.security.*
import javax.net.ssl.*
import kotlin.test.*

class CIOHttpsTest : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Netty, applicationEngineEnvironment {
        sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
            port = serverPort
            keyStorePath = keyStoreFile.absoluteFile

            module {
                routing {
                    get {
                        call.respondText("Hello, world")
                    }
                }
            }
        }

    })

    companion object {
        val keyStoreFile = File("build/temp.jks")
        lateinit var keyStore: KeyStore
        lateinit var sslContext: SSLContext
        lateinit var x509TrustManager: X509TrustManager

        @BeforeClass
        @JvmStatic
        fun setupAll() {
            keyStore = generateCertificate(keyStoreFile)
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)
            x509TrustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }

    }

    @Test
    fun hello(): Unit = runBlocking {
        HttpClient(CIO.config {
            https.apply {
                trustManager = x509TrustManager
            }
        }).use { client ->
            assertEquals("Hello, world", client.get("https://127.0.0.1:$serverPort/"))
        }
    }

    @Test
    fun external(): Unit = runBlocking {
        val client = HttpClient(CIO)

        val response = client.get<HttpResponse>("https://kotlinlang.org")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
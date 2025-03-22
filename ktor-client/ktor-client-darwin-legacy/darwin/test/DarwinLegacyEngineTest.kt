/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.engine.darwin.internal.legacy.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSHTTPCookieStorage.Companion.sharedHTTPCookieStorage
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DarwinLegacyEngineTest : ClientEngineTest<DarwinLegacyClientEngineConfig>(DarwinLegacy) {

    @Test
    fun testRequestInRunBlocking() = runBlocking(Dispatchers.Default) {
        val client = HttpClient(DarwinLegacy)

        try {
            withTimeout(1000) {
                val response = client.get(TEST_SERVER)
                assertEquals("Hello, world!", response.bodyAsText())
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun testQueryWithCyrillic() = testClient(timeout = 1.seconds) {
        test { client ->
            val response = client.get("$TEST_SERVER/echo_query?привет")
            assertEquals("привет=[]", response.bodyAsText())
        }
    }

    @Test
    fun testQueryWithMultipleParams() = testClient(timeout = 1.seconds) {
        test { client ->
            val response = client.get("$TEST_SERVER/echo_query?asd=qwe&asd=123&qwe&zxc=vbn")
            assertEquals("asd=[qwe, 123], qwe=[], zxc=[vbn]", response.bodyAsText())
        }
    }

    @Test
    fun testNSUrlSanitize() {
        assertEquals(
            "http://127.0.0.1/echo_query?%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82",
            stringToNSUrlString("http://127.0.0.1/echo_query?привет")
        )

        val possibleResults = setOf(
            "http://%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82.%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82/",
            "http://xn--b1agh1afp.xn--b1agh1afp/",
        )
        assertTrue(
            stringToNSUrlString("http://привет.привет/") in possibleResults
        )
    }

    @Test
    fun testCookieIsNotPersistedByDefault() = testClient {
        test { client ->
            client.get("$TEST_SERVER/cookies")
            val result = client.get("$TEST_SERVER/cookies/dump")
                .bodyAsText()

            assertEquals("Cookies: ", result)
        }
    }

    @Test
    fun testCookiePersistedWithSessionStore() = testClient {
        config {
            engine {
                configureSession {
                    setHTTPCookieStorage(sharedHTTPCookieStorage)
                }
            }
        }

        test { client ->
            client.get("$TEST_SERVER/cookies")
            val result = client.get("$TEST_SERVER/cookies/dump")
                .bodyAsText()

            assertEquals("Cookies: hello-cookie=my-awesome-value", result)
        }
    }

    @Test
    fun testOverrideDefaultSession(): Unit = testClient {
        config {
            val delegate = KtorLegacyNSURLSessionDelegate()
            val session = NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration(),
                delegate,
                delegateQueue = NSOperationQueue()
            )
            engine {
                usePreconfiguredSession(session, delegate)
            }
        }

        test { client ->
            val response = client.get(TEST_SERVER)
            assertEquals("Hello, world!", response.bodyAsText())
        }
    }

    @Test
    fun testConfigureRequest(): Unit = testClient {
        config {
            engine {
                configureRequest {
                    setValue("my header value", forHTTPHeaderField = "XCustomHeader")
                }
            }
        }

        test { client ->
            val response = client.get("$TEST_SERVER/headers/echo?headerName=XCustomHeader")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("my header value", response.bodyAsText())
        }
    }

    private fun stringToNSUrlString(value: String): String {
        return Url(value).toNSUrl().absoluteString!!
    }
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.engine.darwin.internal.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.*
import platform.Foundation.NSHTTPCookieStorage.Companion.sharedHTTPCookieStorage
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class DarwinEngineTest : ClientEngineTest<DarwinClientEngineConfig>(Darwin) {

    @Test
    fun testRequestInRunBlockingDispatchersDefault() = runBlocking(Dispatchers.Default) {
        val client = HttpClient(Darwin)

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
    fun testRequestInRunBlockingDispatchersUnconfined() = runBlocking(Dispatchers.Unconfined) {
        val client = HttpClient(Darwin)

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
    fun testRequestInRunBlockingDispatchersNone() = runBlocking {
        val client = HttpClient(Darwin)

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
            val delegate = KtorNSURLSessionDelegate()
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
    fun testOverrideDefaultSessionWithWebSockets(): Unit = testClient {
        config {
            val delegate = KtorNSURLSessionDelegate()
            val session = NSURLSession.sessionWithConfiguration(
                NSURLSessionConfiguration.defaultSessionConfiguration(),
                delegate,
                delegateQueue = NSOperationQueue()
            )
            engine {
                usePreconfiguredSession(session, delegate)
            }
            install(WebSockets)
        }

        test { client ->
            val session = client.webSocketSession("$TEST_WEBSOCKET_SERVER/websockets/echo")
            session.send("test")
            val response = session.incoming.receive() as Frame.Text
            assertEquals("test", response.readText())
            session.close()
        }
    }

    // Issue: KTOR-7355
    @Test
    fun testOverrideDefaultSessionWithoutDelegate() {
        val result = runCatching {
            HttpClient(Darwin) {
                engine {
                    usePreconfiguredSession(
                        session = NSURLSession.sessionWithConfiguration(
                            NSURLSessionConfiguration.defaultSessionConfiguration()
                        ),
                        delegate = KtorNSURLSessionDelegate(),
                    )
                }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            result.getOrThrow()
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

    @OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
    @Test
    fun testConfigureWebsocketRequest(): Unit = testClient {
        var customChallengeCalled = false
        config {
            engine {
                handleChallenge { _, _, challenge, completionHandler ->
                    customChallengeCalled = true
                    challenge.protectionSpace.serverTrust?.let {
                        if (challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust) {
                            val credential = NSURLCredential.credentialForTrust(it)
                            completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
                        }
                    }
                }
            }

            install(WebSockets)
        }

        test { client ->
            val session = client.webSocketSession("wss://127.0.0.1:8089/websockets/echo")
            session.send("test")
            val response = session.incoming.receive() as Frame.Text
            assertEquals("test", response.readText())
            assertTrue(customChallengeCalled)
            session.close()
        }
    }

    @Test
    fun testWebSocketPingInterval() = testClient {
        config {
            install(WebSockets) {
                pingInterval = 1.seconds
            }
        }

        test { client ->
            assertFailsWith<TimeoutCancellationException> {
                client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                    withTimeout(5.seconds) {
                        for (frame in incoming) {
                        }
                    }
                }
            }
        }
    }

    @OptIn(UnsafeNumber::class)
    @Test
    fun testRethrowExceptionThrownDuringCustomChallenge() = runBlocking {
        val challengeException = Exception("Challenge failed")

        val client = HttpClient(Darwin) {
            engine {
                handleChallenge { _, _, _, _ -> throw challengeException }
            }
        }

        val thrownException = assertFails { client.get(TEST_SERVER_TLS) }
        assertSame(thrownException, challengeException, "Expected exception to be rethrown")
    }

    private fun stringToNSUrlString(value: String): String {
        return Url(value).toNSUrl().absoluteString!!
    }
}

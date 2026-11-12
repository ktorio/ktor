/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.util.date.*
import kotlin.test.*

class HttpCookiesExpiresTest {
    @Test
    fun `custom expiration is stored and controls subsequent requests`() = testSuspend {
        var now = 1000L
        val storage = AcceptAllCookiesStorage { now }
        val client = cookieClient(storage, "session=abc; Expires=custom; Path=/") {
            expiresParser { GMTDate(2000L) }
        }
        try {
            val response = client.get("http://localhost/set")
            assertEquals("OK", response.bodyAsText())
            assertNull(response.setCookie().single().expires)
            assertEquals(2000L, client.cookies("http://localhost").single().expires?.timestamp)
            assertEquals("session=abc", client.get("http://localhost/echo").bodyAsText())
            now = 2001L
            assertEquals("", client.get("http://localhost/echo").bodyAsText())
        } finally {
            client.close()
        }
    }

    @Test
    fun `custom epoch expiration deletes a previously stored cookie`() = testSuspend {
        val storage = AcceptAllCookiesStorage { 1000L }
        storage.addCookie(Url("http://localhost/"), Cookie("session", "old", path = "/"))
        val client = cookieClient(storage, "session=abc; Expires=0; Path=/") {
            expiresParser { value -> if (value == "0") GMTDate(0L) else null }
        }
        try {
            assertEquals("session=old", client.get("http://localhost/echo").bodyAsText())
            client.get("http://localhost/set").bodyAsText()
            assertEquals("", client.get("http://localhost/echo").bodyAsText())
        } finally {
            client.close()
        }
    }

    @Test
    fun `max age overrides both earlier and later custom expiration`() = testSuspend {
        for (expiration in listOf(0L, 10000L)) {
            var now = 1000L
            val storage = AcceptAllCookiesStorage { now }
            val client = cookieClient(storage, "session=abc; Expires=custom; Max-Age=2; Path=/") {
                expiresParser { GMTDate(expiration) }
            }
            try {
                client.get("http://localhost/set").bodyAsText()
                now = 2000L
                assertEquals("session=abc", client.get("http://localhost/echo").bodyAsText())
                now = 3001L
                assertEquals("", client.get("http://localhost/echo").bodyAsText())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `custom null and failure retain cookies and do not prevent later cookies`() = testSuspend {
        for (throws in listOf(false, true)) {
            val storage = AcceptAllCookiesStorage { 1000L }
            val client = cookieClient(
                storage,
                "first=a; Path=/; Expires=Wed, 21 Oct 2015 07:28:00 GMT,second=b; Expires=custom; Path=/"
            ) {
                expiresParser {
                    if (it == "custom") {
                        GMTDate(2000L)
                    } else {
                        if (throws) error("Unsupported date")
                        null
                    }
                }
            }
            try {
                assertEquals("OK", client.get("http://localhost/set").bodyAsText())
                val cookies = client.cookies("http://localhost")
                assertNull(cookies.single { it.name == "first" }.expires)
                assertEquals(2000L, cookies.single { it.name == "second" }.expires?.timestamp)
                assertEquals("first=a; second=b", client.get("http://localhost/echo").bodyAsText())
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `last parser registration wins and clients remain independent`() = testSuspend {
        lateinit var savedConfig: HttpCookies.Config
        val first = cookieClient(AcceptAllCookiesStorage { 1000L }, "session=abc; Expires=custom; Path=/") {
            savedConfig = this
            expiresParser { error("Replaced parser must not run") }
            expiresParser { GMTDate(2000L) }
        }
        val second = cookieClient(AcceptAllCookiesStorage { 1000L }, "session=abc; Expires=custom; Path=/") {
            expiresParser { GMTDate(3000L) }
        }
        try {
            savedConfig.expiresParser { GMTDate(4000L) }
            first.get("http://localhost/set").bodyAsText()
            second.get("http://localhost/set").bodyAsText()
            assertEquals(2000L, first.cookies("http://localhost").single().expires?.timestamp)
            assertEquals(3000L, second.cookies("http://localhost").single().expires?.timestamp)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `unconfigured client retains built-in date parsing`() = testSuspend {
        val date = "Wed, 21 Oct 2015 07:28:00 GMT"
        val client = cookieClient(AcceptAllCookiesStorage { 1000L }, "session=abc; Expires=$date; Path=/")
        try {
            client.get("http://localhost/set").bodyAsText()
            assertEquals(date.fromCookieToGmtDate(), client.cookies("http://localhost").single().expires)
        } finally {
            client.close()
        }
    }

    private fun cookieClient(
        storage: CookiesStorage,
        header: String,
        configure: HttpCookies.Config.() -> Unit = {}
    ): HttpClient = HttpClient(MockEngine) {
        install(HttpCookies) {
            this.storage = storage
            configure()
        }
        engine {
            addHandler { request ->
                if (request.url.encodedPath == "/set") {
                    respond("OK", headers = headersOf(HttpHeaders.SetCookie, header))
                } else {
                    respond(request.headers[HttpHeaders.Cookie].orEmpty())
                }
            }
        }
    }
}

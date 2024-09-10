/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.server.util.*
import io.ktor.server.websocket.*
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.*
import java.text.*
import java.time.*
import java.time.format.*
import kotlin.test.*

class CookiesTest {

    @Test
    fun `add cookies with expires`() {
        val date = LocalDate
            .parse("20150818", DateTimeFormatter.ofPattern("yyyyMMdd"))
            .atStartOfDay(ZoneId.of("GMT"))!!
            .toGMTDate()

        testSetCookies("SESSION=123; Expires=Tue, 18 Aug 2015 00:00:00 GMT") {
            cookies.append("SESSION", "123", expires = date)
        }
    }

    @Test
    fun `add cookies old Date`() {
        val date = SimpleDateFormat("yyyyMMdd z")
            .parse("20150818 GMT")
        testSetCookies("SESSION=123; Expires=Tue, 18 Aug 2015 00:00:00 GMT") {
            cookies.append("SESSION", "123", expires = date.toInstant().toGMTDate())
        }
    }

    @Test
    fun `add cookies with expires at specified time zone`() {
        val zoneId = ZoneId.ofOffset("UTC", ZoneOffset.ofHours(3))
        val date = LocalDate
            .parse("20150818", DateTimeFormatter.ofPattern("yyyyMMdd"))
            .atStartOfDay(zoneId)

        testSetCookies("SESSION=123; Expires=Mon, 17 Aug 2015 21:00:00 GMT") {
            cookies.append("SESSION", "123", expires = date.toGMTDate())
        }
    }

    private fun testSetCookies(expectedHeaderContent: String, block: RoutingResponse.() -> Unit) = testApplication {
        routing {
            get("/set-cookie") {
                call.response.apply(block)
            }
        }

        val response = client.get("/set-cookie")
        assertEquals(expectedHeaderContent, response.headers["Set-Cookie"]?.cutSetCookieHeader())
    }

    private fun String.cutSetCookieHeader() = substringBeforeLast("\$x-enc").trimEnd().removeSuffix(";")
}

class SecureCookiesTest {
    @Test
    fun `consider wss and https to be secure contexts`() = testApplication {
        @Serializable
        data class User(val id: String)

        install(WebSockets)
        install(Sessions) {
            cookie<User>("c") {
                cookie.secure = true
            }
        }
        install(XForwardedHeaders)

        routing {
            get("/") {
                call.sessions.set("c", User("123"))
            }
            webSocket("wss") {
            }
        }

        val client = createClient {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }

        val r = client.get("/") {
            header(HttpHeaders.XForwardedProto, "https")
        }
        val setCookieHeader = r.headers[HttpHeaders.SetCookie]
        assertNotNull(setCookieHeader)
        val c = parseServerSetCookieHeader(setCookieHeader)

        client.webSocket("/wss", request = {
            cookie(c.name, c.value, path = c.path)
            header(HttpHeaders.XForwardedProto, "https")
        }) {}

        client.webSocket("/wss", request = {
            cookie(c.name, c.value, path = c.path)
            header(HttpHeaders.XForwardedProto, "wss")
        }) {}
    }
}

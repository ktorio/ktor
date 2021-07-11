import io.ktor.client.features.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import kotlin.test.*

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

class CookiesTest {

    @Test
    fun testCookiesEscape() = testSuspend {
        val storage = AcceptAllCookiesStorage()
        val cookie = parseServerSetCookieHeader(
            "JSESSIONID=jc1wDGgCjR8s72-xdZYYZsLywZdCsiIT86U7X5h7.front10; HttpOnly"
        )
        storage.addCookie("http://localhost/", cookie)

        val feature = HttpCookies(storage, emptyList())
        val builder = HttpRequestBuilder()

        feature.sendCookiesWith(builder)

        assertEquals(
            "JSESSIONID=jc1wDGgCjR8s72-xdZYYZsLywZdCsiIT86U7X5h7.front10;",
            builder.headers[HttpHeaders.Cookie]
        )
    }
}

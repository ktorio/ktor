/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.routing.*
import kotlin.test.*

public fun Application.cookiesTest() {
    routing {
        route("cookies") {
            get {
                val cookie = Cookie("hello-cookie", "my-awesome-value", domain = "127.0.0.1")
                call.response.cookies.append(cookie)

                call.respond("Done")
            }
            get("/update-user-id") {
                val id = call.request.cookies["id"]?.toInt() ?: let {
                    call.response.status(HttpStatusCode.Forbidden)
                    call.respondText("Forbidden")
                    return@get
                }

                with(call.response.cookies) {
                    append(Cookie("id", (id + 1).toString(), domain = "127.0.0.1", path = "/"))
                    append(Cookie("user", "ktor", domain = "127.0.0.1", path = "/"))
                }

                call.respond("Done")
            }
            get("/multiple") {
                val cookies = call.request.cookies
                val first = cookies["first"] ?: fail()
                val second = cookies["second"] ?: fail()

                assertEquals("first-cookie", first)
                assertEquals("second-cookie", second)
                call.respond("Multiple done")
            }
            get("/withPath") {
                val cookie = Cookie("marker", "value", path = "cookies/withPath/")
                call.response.cookies.append(cookie)
                call.respond("OK")
            }
            get("/withPath/something") {
                val cookies = call.request.cookies
                if (cookies["marker"] == "value") {
                    call.respond("OK")
                } else {
                    call.respond(HttpStatusCode.BadRequest)
                }
            }
            get("/foo") {
                val cookie = Cookie("foo", "bar")
                call.response.cookies.append(cookie)

                call.respond("OK")
            }
            get("/FOO") {
                assertTrue(call.request.cookies.rawCookies.isEmpty())
                call.respond("OK")
            }
            get("/expire") {
                call.request.cookies.rawCookies.forEach { (name, _) ->
                    call.response.cookies.appendExpired(name, path = "/")
                }
                call.respond("OK")
            }
            get("/multiple-comma") {
                val cookies = call.request.cookies
                val first = cookies["fir,st"] ?: fail()
                val second = cookies["sec,ond"] ?: fail()

                assertEquals("first, cookie", first)
                assertEquals("second, cookie", second)

                with(call.response.cookies) {
                    append(Cookie("third", "third cookie", domain = "127.0.0.1", path = "/"))
                    append(Cookie("fourth", "fourth cookie", domain = "127.0.0.1", path = "/"))
                }
                call.respond("Multiple done")
            }
            get("/encoded") {
                call.respond(call.request.header(HttpHeaders.Cookie) ?: fail())
            }
            get("/respond-single-cookie") {
                call.respond(call.request.cookies["single"] ?: fail())
            }
            get("/respond-a-minus-b") {
                val a = call.request.cookies["a"]?.toInt() ?: fail()
                val b = call.request.cookies["b"]?.toInt() ?: fail()

                call.respond((a - b).toString())
            }
        }
    }
}

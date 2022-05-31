/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.cookiesTest() {
    routing {
        route("cookies") {
            get {
                val cookie = Cookie("hello-cookie", "my-awesome-value", domain = "127.0.0.1")
                context.response.cookies.append(cookie)

                context.respond("Done")
            }
            get("dump") {
                val text = call.request.cookies.rawCookies.entries.joinToString()
                call.respondText("Cookies: $text")
            }
            get("/update-user-id") {
                val id = context.request.cookies["id"]?.toInt() ?: let {
                    context.response.status(HttpStatusCode.Forbidden)
                    context.respondText("Forbidden")
                    return@get
                }

                with(context.response.cookies) {
                    append(Cookie("id", (id + 1).toString(), domain = "127.0.0.1", path = "/"))
                    append(Cookie("user", "ktor", domain = "127.0.0.1", path = "/"))
                }

                context.respond("Done")
            }
            get("/multiple") {
                val cookies = context.request.cookies
                val first = cookies["first"] ?: error("First cookie not found")
                val second = cookies["second"] ?: error("Second cookie not found")

                check("first-cookie" == first)
                check("second-cookie" == second)
                context.respond("Multiple done")
            }
            get("/withPath") {
                val cookie = Cookie("marker", "value", path = "cookies/withPath/")
                context.response.cookies.append(cookie)
                context.respond("OK")
            }
            get("/withPath/something") {
                val cookies = context.request.cookies
                if (cookies["marker"] == "value") {
                    context.respond("OK")
                } else {
                    context.respond(HttpStatusCode.BadRequest)
                }
            }
            get("/foo") {
                val cookie = Cookie("foo", "bar")
                context.response.cookies.append(cookie)

                call.respond("OK")
            }
            get("/FOO") {
                val cookies = call.request.cookies
                if (cookies.rawCookies.isNotEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Cookies: ${cookies.rawCookies.entries.joinToString()}")
                } else {
                    call.respond("OK")
                }
            }
            get("/expire") {
                call.request.cookies.rawCookies.forEach { (name, _) ->
                    call.response.cookies.appendExpired(name, path = "/")
                }
                call.respond("OK")
            }
            get("/multiple-comma") {
                val cookies = context.request.cookies
                val first = cookies["fir,st"] ?: error("First not found")
                val second = cookies["sec,ond"] ?: error("Second not found")

                check("first, cookie" == first)
                check("second, cookie" == second)

                with(context.response.cookies) {
                    append(Cookie("third", "third cookie", domain = "127.0.0.1", path = "/"))
                    append(Cookie("fourth", "fourth cookie", domain = "127.0.0.1", path = "/"))
                }
                context.respond("Multiple done")
            }
            get("/encoded") {
                context.respond(context.request.header(HttpHeaders.Cookie) ?: error("Cookie header not found"))
            }
            get("/respond-single-cookie") {
                context.respond(context.request.cookies["single"] ?: error("Cookie single not found"))
            }
            get("/respond-a-minus-b") {
                val a = context.request.cookies["a"]?.toInt() ?: error("Cookie a not found")
                val b = context.request.cookies["b"]?.toInt() ?: error("Cookie b not found")

                context.respond((a - b).toString())
            }
        }
    }
}

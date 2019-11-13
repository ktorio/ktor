/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

internal fun Application.redirectTest() {
    routing {
        route("/redirect") {
            get("/") {
                call.respondRedirect("/redirect/get")
            }
            get("/get") {
                call.respondText("OK")
            }
            post("/post") {
                call.respondText("OK")
            }
            get("/infinity") {
                call.respondRedirect("/redirect/infinity")
            }
            get("/cookie") {
                val token = call.request.cookies["Token"] ?: run {
                    call.response.cookies.append("Token", "Hello")
                    call.respondRedirect("/redirect/cookie")
                    return@get
                }

                check("Hello" == token)
                call.respondText("OK")
            }
            get("/directory/redirectFile") {
                call.respondRedirect("/redirect/directory/targetFile")
            }
            get("/directory/targetFile") {
                call.respondText("targetFile")
            }
            get("/directory/absoluteRedirectFile") {
                call.respondRedirect("/redirect/directory2/absoluteTargetFile")
            }
            get("/directory2/absoluteTargetFile") {
                call.respondText("absoluteTargetFile")
            }
            get("/directory/hostAbsoluteRedirect") {
                call.respondRedirect("https://httpstat.us/200")
            }
            post("/post-expecting-get-301") {
                call.response.headers.append(HttpHeaders.Location, "/redirect/get")
                call.respond(HttpStatusCode.MovedPermanently)
            }
            post("/post-expecting-get-302") {
                call.response.headers.append(HttpHeaders.Location, "/redirect/get")
                call.respond(HttpStatusCode.Found)
            }
            post("/post-expecting-post") {
                call.response.headers.append(HttpHeaders.Location, "/redirect/post")
                call.respond(HttpStatusCode.TemporaryRedirect)
            }

            for(n in 1..10) {
                get("/count/$n") {
                    if(n > 1) {
                        call.respondRedirect("/redirect/count/${n-1}")
                    } else {
                        call.respondRedirect("/redirect/get")
                    }
                }
            }
        }
    }
}

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.client.tests.utils.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

internal fun Application.redirectTest() {
    routing {
        route("/redirect") {
            get {
                call.respondRedirect("/redirect/get")
            }
            get("/get") {
                call.respondText("OK")
            }
            get("/getWithUri") {
                call.respondText(call.request.uri)
            }
            get("/infinity") {
                call.respondRedirect("/redirect/infinity")
            }
            get("/encodedQuery") {
                call.respondRedirect("/redirect/getWithUri?key=value1%3Bvalue2%3D%22some=thing")
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
                call.respondRedirect("$TEST_SERVER/redirect/get")
            }
            get("/multipleRedirects/login") {
                call.respondRedirect("/redirect/multipleRedirects/user/")
            }
            get("/multipleRedirects/user/") {
                call.respondRedirect("account/details")
            }
            get("/multipleRedirects/user/account/details") {
                call.respondText("account details")
            }
        }
    }
}

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests


import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun main() {
    embeddedServer(Netty, 5000) { module(CookieEncoding.BASE64_ENCODING) }
        .start(true)
}

data class MySession(val name: String, val id: Int)

fun Application.module(encoding: CookieEncoding) {
    install(Sessions) {
        cookie<MySession>("id", SessionStorageMemory()) {
            cookie.encoding = encoding
        }
    }
    routing {
        get {
            val session = call.sessions.get<MySession>()
            if (session != null) {
                call.respond(session.id.toString())
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Error")
            }
        }

        get("login") {
            val session = MySession("test", 1)
            call.sessions.set(session)
            call.respond("Logged in")
        }
    }
}

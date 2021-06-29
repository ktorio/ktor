/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.server.application.*
import io.ktor.server.features.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.encodingTestServer() {
    routing {
        route("/compression") {
            route("/deflate") {
                install(Compression) { deflate() }
                setCompressionEndpoints()
            }
            route("/gzip") {
                install(Compression) { gzip() }
                setCompressionEndpoints()
            }
            route("/identity") {
                install(Compression) { identity() }
                setCompressionEndpoints()
            }
        }
    }
}

private fun Route.setCompressionEndpoints() {
    get {
        call.respondText("Compressed response!")
    }
}

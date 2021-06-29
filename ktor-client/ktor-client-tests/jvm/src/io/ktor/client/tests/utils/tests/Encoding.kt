/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.features.*

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

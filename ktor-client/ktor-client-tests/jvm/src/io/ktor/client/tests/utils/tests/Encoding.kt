/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*

internal fun Application.encodingTestServer() {
    install(Compression) {
        deflate()
        gzip()
        identity()
    }
    routing {
        route("/compression") {
            route("/deflate") {
                setCompressionEndpoints()
            }
            route("/gzip") {
                setCompressionEndpoints()
            }
            route("/identity") {
                setCompressionEndpoints()
            }
        }
    }
}

private fun RoutingBuilder.setCompressionEndpoints() {
    get {
        call.respondText("Compressed response!")
    }
}

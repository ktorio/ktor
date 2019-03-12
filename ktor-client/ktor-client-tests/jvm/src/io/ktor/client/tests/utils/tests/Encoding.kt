package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*

fun Application.encodingTestServer() {
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

// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.server.application.Application
import io.ktor.server.request.header
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.getOrFail
import io.ktor.server.util.getValue

fun Application.installParameters() {
    routing {
        /**
         * @tag parameters
         */
        get("/parameters/{a}/{b}/{c}") {
            call.respondText(listOf(
                call.parameters["a"],
                call.pathParameters["b"],
                call.request.pathVariables["c"],
                call.queryParameters["d"],
                call.request.queryParameters["e"],
                call.request.queryParameters.getAll("f"),
                call.request.queryParameters.getOrFail("g"),
                call.request.headers["h"],
                call.request.headers.getAll("i"),
                call.request.header("j")
            ).joinToString())
        }

        get("/parameters/delegates/{a}/{b}") {
            val a: String by call.parameters
            val b: Int by call.pathParameters
            val c: String by call.queryParameters

            call.respondText(listOf(a, b, c).joinToString())
        }
    }
}

/* GENERATED_FIR_TAGS: funWithExtensionReceiver, functionDeclaration */

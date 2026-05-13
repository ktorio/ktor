// RUN_PIPELINE_TILL: BACKEND
package openapi

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.routing.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

@Serializable
data class Node(val name: String, val parent: Node?)

fun Application.installRecursion() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        post("/node") {
            val node = call.receive<Node>()
            call.respond(node)
        }
    }
}

/* GENERATED_FIR_TAGS: classDeclaration, data, funWithExtensionReceiver, functionDeclaration, nullableType,
primaryConstructor, propertyDeclaration */
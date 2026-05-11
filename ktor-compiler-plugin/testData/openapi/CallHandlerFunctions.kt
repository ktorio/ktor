// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingRequest
import io.ktor.server.routing.RoutingResponse
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.installCallHandlerFunctions() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        get("/call-receiver-extension") {
            call.respondHello()
        }
        get("/request-parameters") {
            val recipient = getRecipient(call.request)
            call.respondHello(ContentType.Text.Plain, recipient)
        }
        get("/response-header") {
            val recipient = getRecipient(call.request)
            call.response.includeRecipientAndRespond(recipient)
        }
        get("/recursive-function") {
            call.respondRecursive()
        }
        get("/passing-arguments") {
            call.includeHeader("X-Foo", "Bar")
            call.respondText("{}", contentType = ContentType.Application.Json)
        }
        get("/reified-type-parameter") {
            call.respondReified(setOf(42))
        }
    }
}

private suspend fun ApplicationCall.respondHello(
    contentType: ContentType = ContentType.Text.Plain,
    recipient: String = "World"
) {
    when(contentType) {
        ContentType.Text.Plain -> respondText("Hello, $recipient!")
        ContentType.Text.Html ->
            respondText(
                contentType = ContentType.Text.Html,
                text = """<h1>Hello, $recipient!</h1>"""
            )
    }
}

private fun getRecipient(request: RoutingRequest) =
    request.queryParameters["recipient"] ?: "World"

private suspend fun RoutingResponse.includeRecipientAndRespond(recipient: String) {
    headers.append("X-Recipient", recipient)
    call.respondHello(ContentType.Text.Html, recipient)
}

private suspend fun RoutingCall.respondRecursive() {
    respondText(nextHeader())
}

private suspend inline fun <reified E: Any> RoutingCall.respondReified(e: E) {
    respond(e)
}

private fun RoutingCall.nextHeader(index: Int = 0): String = buildString {
    val headersList = request.headers.entries().toList()
    append(headersList[index].value).append("\n")
    if (index + 1 < headersList.size) append(nextHeader(index + 1))
}

private fun RoutingCall.includeHeader(key: String, value: String) {
    response.headers.append(key, value)
}

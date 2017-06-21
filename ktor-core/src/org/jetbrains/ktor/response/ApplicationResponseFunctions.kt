package org.jetbrains.ktor.response

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*

/**
 * Sends a [message] as a response
 */
suspend fun ApplicationCall.respond(message: Any) {
    sendPipeline.execute(this, message)
}

suspend fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false) {
    response.headers.append(HttpHeaders.Location, url)
    respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

suspend fun ApplicationCall.respondText(text: String, contentType: ContentType) = respond(TextContent(text, contentType))
suspend fun ApplicationCall.respondText(text: String) = respondText(text, ContentType.Text.Plain)



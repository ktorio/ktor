package org.jetbrains.ktor.response

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*

suspend fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false) {
    response.headers.append(HttpHeaders.Location, url)
    respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

suspend fun ApplicationCall.respondText(contentType: ContentType, text: String) = respond(TextContent(contentType, text))
suspend fun ApplicationCall.respondText(text: String) = respondText(ContentType.Text.Plain, text)



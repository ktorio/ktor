package org.jetbrains.ktor.response

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*

fun ApplicationCall.respondRedirect(url: String, permanent: Boolean = false): Nothing {
    response.headers.append(HttpHeaders.Location, url)
    respond(if (permanent) HttpStatusCode.MovedPermanently else HttpStatusCode.Found)
}

fun ApplicationCall.respondText(contentType: ContentType, text: String): Nothing = respond(TextContent(contentType, text))
fun ApplicationCall.respondText(text: String): Nothing = respondText(ContentType.Text.Plain, text)



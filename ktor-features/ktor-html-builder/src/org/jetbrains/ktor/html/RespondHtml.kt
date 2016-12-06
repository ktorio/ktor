package org.jetbrains.ktor.html

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*

fun ApplicationCall.respondHtml(statusCode: HttpStatusCode = HttpStatusCode.OK, body: HTML.() -> Unit): Nothing {
    response.status(statusCode)
    response.contentType(ContentType.Text.Html)
    respondWrite { appendHTML(true).html(body) }
}

package io.ktor.freemarker

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*

suspend fun ApplicationCall.respondTemplate(template: String, model: Any? = null, etag: String? = null, contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8))
    = respond(FreeMarkerContent(template, model, etag, contentType))

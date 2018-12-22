package io.ktor.freemarker

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*

/**
 * Respond with the specified [template] passing [model]
 *
 * @see FreeMarkerContent
 */
suspend fun ApplicationCall.respondTemplate(template: String, model: Any? = null, etag: String? = null, contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8))
    = respond(FreeMarkerContent(template, model, etag, contentType))

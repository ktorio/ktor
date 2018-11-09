package io.ktor.mustache

import io.ktor.application.ApplicationCall
import io.ktor.http.ContentType
import io.ktor.http.withCharset
import io.ktor.response.respond


/**
 * Respond with the specified [template] passing [model]
 *
 * @see MustacheContent
 */
suspend fun ApplicationCall.respondTemplate(
    template: String,
    model: Any? = null,
    etag: String? = null,
    contentType: ContentType = ContentType.Text.Html.withCharset(
        Charsets.UTF_8
    )
) = respond(MustacheContent(template, model, etag, contentType))

package io.ktor.freemarker

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*

suspend fun ApplicationCall.respondTemplate(templateName: String, model: Any = emptyMap<String, Any>(), etag: String? = null, contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)) 
    = respond(FreeMarkerContent(templateName, model, etag, contentType))

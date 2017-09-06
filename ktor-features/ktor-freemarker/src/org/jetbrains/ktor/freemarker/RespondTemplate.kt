package org.jetbrains.ktor.freemarker

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*

suspend fun ApplicationCall.respondTemplate(templateName: String, model: Any = emptyMap<String, Any>(), etag: String? = null, contentType: ContentType = ContentType.Text.Html.withCharset(Charsets.UTF_8)) 
    = respond(FreeMarkerContent(templateName, model, etag, contentType))

package io.ktor.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.ApplicationCall
import io.ktor.content.IncomingContent
import io.ktor.content.readText
import io.ktor.features.ContentConverter
import io.ktor.features.ConvertedContent
import io.ktor.http.ContentType
import io.ktor.http.withCharset
import io.ktor.pipeline.PipelineContext
import io.ktor.request.ApplicationReceiveRequest

/**
 *    install(ContentNegotiation) {
 *       register(ContentType.Application.Json, jacksonObjectMapper())
 *    }
 */
class JacksonConverter(private val objectmapper: ObjectMapper = jacksonObjectMapper()) : ContentConverter {
    private val contentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

    override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, value: Any): Any? {
        return ConvertedContent(objectmapper.writeValueAsString(value), contentType)
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val value = request.value as? IncomingContent ?: return null
        val type = request.type
        return objectmapper.readValue(value.readText(), type.javaObjectType)
    }
}

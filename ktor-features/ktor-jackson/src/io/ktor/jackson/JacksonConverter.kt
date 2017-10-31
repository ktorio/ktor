package io.ktor.jackson

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*

/**
 *    install(ContentNegotiation) {
 *       register(ContentType.Application.Json, JacksonConverter())
 *    }
 *
 *    to be able to modify the objectMapper (eg. using specific modules and/or serializers and/or
 *    configuration options, you could use the following (as seen in the ktor-samples):
 *
 *    install(ContentNegotiation) {
 *        jackson {
 *            configure(SerializationFeature.INDENT_OUTPUT, true)
 *            registerModule(JavaTimeModule())
 +        }
 *    }
 */
class JacksonConverter(private val objectmapper: ObjectMapper = jacksonObjectMapper()) : ContentConverter {
    override suspend fun convertForSend(context: PipelineContext<Any, ApplicationCall>, contentType: ContentType, value: Any): Any? {
        return TextContent(objectmapper.writeValueAsString(value), contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val value = request.value as? IncomingContent ?: return null
        val type = request.type
        return objectmapper.readValue(value.readText(), type.javaObjectType)
    }
}

fun ContentNegotiation.Configuration.jackson(block: ObjectMapper.() -> Unit) {
    val mapper = jacksonObjectMapper()
    mapper.apply(block)
    val converter = JacksonConverter(mapper)
    register(ContentType.Application.Json, converter)
}

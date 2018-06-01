package io.ktor.jackson

import com.fasterxml.jackson.core.util.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*

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
        val type = request.type
        val value = request.value as? ByteReadChannel ?: return null
        val reader = value.toInputStream().reader(context.call.request.contentCharset() ?: Charsets.UTF_8)
        return objectmapper.readValue(reader, type.javaObjectType)
    }
}

fun ContentNegotiation.Configuration.jackson(block: ObjectMapper.() -> Unit) {
    val mapper = jacksonObjectMapper()
    mapper.apply {
        setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
            indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
            indentObjectsWith(DefaultIndenter("  ", "\n"))
        })
    }
    mapper.apply(block)
    val converter = JacksonConverter(mapper)
    register(ContentType.Application.Json, converter)
}

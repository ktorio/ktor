/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.jackson

import com.fasterxml.jackson.core.util.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlin.reflect.jvm.*

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
public class JacksonConverter(private val objectmapper: ObjectMapper = jacksonObjectMapper()) : ContentConverter {
    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        val charset = context.call.suitableCharset()
        return OutputStreamContent(
            {
                if (charset == Charsets.UTF_8) {
                    /*
                    Jackson internally does special casing on UTF-8, presumably for performance reasons. Thus we pass an
                    InputStream instead of a writer to let Jackson do it's thing.
                     */
                    objectmapper.writeValue(this, value)
                } else {
                    objectmapper.writeValue(this.writer(charset = charset), value)
                }
            },
            contentType.withCharset(charset)
        )
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val type = request.typeInfo
        val value = request.value as? ByteReadChannel ?: return null
        return withContext(Dispatchers.IO) {
            val reader = value.toInputStream().reader(context.call.request.contentCharset() ?: Charsets.UTF_8)
            objectmapper.readValue(reader, type.jvmErasure.javaObjectType)
        }
    }
}

/**
 * Register Jackson converter into [ContentNegotiation] feature
 */
public fun ContentNegotiation.Configuration.jackson(
    contentType: ContentType = ContentType.Application.Json,
    block: ObjectMapper.() -> Unit = {}
) {
    val mapper = ObjectMapper()
    mapper.apply {
        setDefaultPrettyPrinter(
            DefaultPrettyPrinter().apply {
                indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                indentObjectsWith(DefaultIndenter("  ", "\n"))
            }
        )
    }
    mapper.apply(block)
    mapper.registerKotlinModule()
    val converter = JacksonConverter(mapper)
    register(contentType, converter)
}

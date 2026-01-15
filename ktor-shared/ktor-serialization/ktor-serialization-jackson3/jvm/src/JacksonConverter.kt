/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.jackson3

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tools.jackson.core.*
import tools.jackson.core.StreamWriteFeature
import tools.jackson.core.util.*
import tools.jackson.databind.*
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.*
import java.io.*
import kotlin.text.*

/**
 * A content converter that uses Jackson
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.serialization.jackson3.JacksonConverter)
 *
 * @param objectMapper a configured instance of [ObjectMapper]
 * @param streamRequestBody if set to true, will stream request body, without keeping it whole in memory.
 * This will set `Transfer-Encoding: chunked` header.
 */
public class JacksonConverter(
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val streamRequestBody: Boolean = true
) : ContentConverter {

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent {
        if (!streamRequestBody && typeInfo.type != Flow::class) {
            return TextContent(
                objectMapper.writeValueAsString(value),
                contentType.withCharsetIfNeeded(charset)
            )
        }
        return OutputStreamContent(
            {
                /*
                Jackson internally does special casing on UTF-8, presumably for performance reasons.
                Thus, we pass an InputStream instead of a Writer to let Jackson do its thing.
                 */
                if (charset == Charsets.UTF_8) {
                    val jGenerator = objectMapper.createGenerator(this, JsonEncoding.UTF8)

                    if (typeInfo.type == Flow::class) {
                        serialize(value as Flow<*>, jGenerator, this)
                    } else {
                        objectMapper.writeValue(jGenerator, value)
                    }
                } else {
                    // For other charsets, we use a Writer
                    val writer = this.writer(charset = charset)
                    val jGenerator = objectMapper.createGenerator(writer)

                    if (typeInfo.type == Flow::class) {
                        serialize(value as Flow<*>, jGenerator, writer)
                    } else {
                        objectMapper.writeValue(jGenerator, value)
                    }
                }
            },
            contentType.withCharsetIfNeeded(charset)
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        try {
            return withContext(Dispatchers.IO) {
                val type = objectMapper.constructType(typeInfo.reifiedType)
                val objectReader = objectMapper.readerFor(type)

                // Jackson handles decoding of Unicode charsets automatically, no need to create a reader.
                // Additionally, a byte-based source is required for binary format (Smile).
                if (isUnicode(charset)) {
                    objectReader.readValue(content.toInputStream())
                } else {
                    val reader = content.toInputStream().reader(charset)
                    objectReader.readValue(reader)
                }
            }
        } catch (cause: Exception) {
            val convertException = JsonConvertException("Illegal json parameter found: ${cause.message}", cause)

            when (cause) {
                is JacksonException -> throw convertException
                else -> throw cause
            }
        }
    }

    private companion object {
        private val jacksonEncodings = buildSet<String> {
            JsonEncoding.entries.forEach { add(it.javaName) }
            add("US-ASCII") // Jackson automatically unescapes Unicode characters
        }

        private fun isUnicode(charset: Charset): Boolean {
            return jacksonEncodings.contains(charset.name()) ||
                charset == Charsets.UTF_16 ||
                charset == Charsets.UTF_32
        }
    }

    /**
     * Cannot use ObjectMapper write to Stream because it flushes the OutputStream on each write
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun <Stream : Flushable, T> serialize(
        flow: Flow<T>,
        jGenerator: JsonGenerator,
        stream: Stream
    ) {
        jGenerator.configure(StreamWriteFeature.FLUSH_PASSED_TO_STREAM, false)
        jGenerator.writeStartArray()

        flow.collect { value ->
            objectMapper.writeValue(jGenerator, value)
            stream.flush()
        }

        jGenerator.writeEndArray()
        jGenerator.flush()
        stream.flush()
    }
}

/**
 * Registers the `application/json` content type to the [ContentNegotiation] plugin using Jackson.
 *
 * You can learn more from the corresponding [client](https://ktor.io/docs/client-serialization.html#-3bcvpz_158) and [server](https://ktor.io/docs/server-serialization.html#-230zkf_175) documentation.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.serialization.jackson.jackson)
 *
 * @param contentType the content type to send with request
 * @param streamRequestBody if set to true, will stream request body, without keeping it whole in memory.
 * This will set `Transfer-Encoding: chunked` header.
 * @param block a configuration block for [JsonMapper.Builder]
 */
public fun Configuration.jackson(
    contentType: ContentType = ContentType.Application.Json,
    streamRequestBody: Boolean = true,
    block: JsonMapper.Builder.() -> Unit = {}
) {
    val builder = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .defaultPrettyPrinter(
            DefaultPrettyPrinter().apply {
                indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance())
                indentObjectsWith(DefaultIndenter("  ", "\n"))
            }
        )

    builder.apply(block)
    val mapper = builder.build()

    val converter = JacksonConverter(mapper, streamRequestBody)
    register(contentType, converter)
}

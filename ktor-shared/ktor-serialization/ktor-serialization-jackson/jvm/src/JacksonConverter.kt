/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.serialization.jackson

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.core.util.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import kotlin.text.*

/**
 * A content converter that uses [Jackson]
 *
 * @param objectMapper a configured instance of [ObjectMapper]
 * @param streamRequestBody if set to true, will stream request body, without keeping it whole in memory.
 * This will set `Transfer-Encoding: chunked` header.
 */
public class JacksonConverter(
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
    private val streamRequestBody: Boolean = true
) : ContentConverter {

    @Deprecated(
        "Use JacksonConverter(objectMapper, streamRequestBody) instead.",
        level = DeprecationLevel.HIDDEN,
    )
    public constructor(objectMapper: ObjectMapper = jacksonObjectMapper()) : this(objectMapper, true)

    @Suppress("OverridingDeprecatedMember")
    @Deprecated(
        "Please override and use serializeNullable instead",
        level = DeprecationLevel.WARNING,
        replaceWith = ReplaceWith("serializeNullable(charset, typeInfo, contentType, value)")
    )
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): OutgoingContent {
        return serializeNullable(contentType, charset, typeInfo, value)
    }

    override suspend fun serializeNullable(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ): OutgoingContent {
        if (!streamRequestBody) {
            // specific behavior for kotlinx.coroutines.flow.Flow : collect it into a List
            val resolvedValue = if (typeInfo.type == Flow::class) {
                (value as Flow<*>).toList()
            } else {
                value
            }
            return TextContent(
                objectMapper.writeValueAsString(resolvedValue),
                contentType.withCharsetIfNeeded(charset)
            )
        }
        return OutputStreamContent(
            {
                /*
                Jackson internally does special casing on UTF-8, presumably for performance reasons. Thus we pass an
                InputStream instead of a Writer to let Jackson do its thing.
                */
                if (charset == Charsets.UTF_8) {
                    // specific behavior for kotlinx.coroutines.flow.Flow : emit asynchronous values in OutputStream
                    if (typeInfo.type == Flow::class) {
                        (value as Flow<*>).serializeJson(this)
                    } else {
                        // non flow content
                        objectMapper.writeValue(this, value)
                    }
                } else {
                    // For other charsets, we use a Writer
                    val writer = this.writer(charset = charset)

                    // specific behavior for kotlinx.coroutines.flow.Flow : emit asynchronous values in Writer
                    if (typeInfo.type == Flow::class) {
                        (value as Flow<*>).serializeJson(writer)
                    } else {
                        // non flow content
                        objectMapper.writeValue(writer, value)
                    }
                }
            },
            contentType.withCharsetIfNeeded(charset)
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        try {
            return withContext(Dispatchers.IO) {
                val reader = content.toInputStream().reader(charset)
                objectMapper.readValue(reader, objectMapper.constructType(typeInfo.reifiedType))
            }
        } catch (deserializeFailure: Exception) {
            val convertException = JsonConvertException("Illegal json parameter found", deserializeFailure)

            when (deserializeFailure) {
                is JsonParseException -> throw convertException
                is JsonMappingException -> throw convertException
                else -> throw deserializeFailure
            }
        }
    }

    private companion object {
        private const val beginArrayCharCode = '['.code
        private const val endArrayCharCode = ']'.code
        private const val objectSeparator = ','.code
    }

    /**
     * Guaranteed to be called inside a [Dispatchers.IO] context, see [OutputStreamContent]
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun <T> Flow<T>.serializeJson(outputStream: OutputStream) {
        val jfactory = JsonFactory()
        // cannot use ObjectMapper write to Stream because it flushes the OutputStream
        val jGenerator = jfactory.createGenerator(outputStream, JsonEncoding.UTF8)
        jGenerator.codec = objectMapper

        outputStream.write(beginArrayCharCode)
        collectIndexed { index, value ->
            if (index > 0) {
                outputStream.write(objectSeparator)
            }
            jGenerator.writeObject(value)
        }
        outputStream.write(endArrayCharCode)
    }

    /**
     * Guaranteed to be called inside a [Dispatchers.IO] context, see [OutputStreamContent]
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun <T> Flow<T>.serializeJson(writer: Writer) {
        val jfactory = JsonFactory()
        // cannot use ObjectMapper write to Stream because it flushes the OutputStream
        val jGenerator = jfactory.createGenerator(writer)
        jGenerator.configure(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM, false)
        jGenerator.codec = objectMapper

        writer.write(beginArrayCharCode)
        collectIndexed { index, value ->
            if (index > 0) {
                writer.write(objectSeparator)
            }
            jGenerator.writeObject(value)
        }
        writer.write(endArrayCharCode)
        // must flush manually
        writer.flush()
    }
}

/**
 * Registers the `application/json` content type to the [ContentNegotiation] plugin using Jackson.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 */
@Deprecated("This will be removed.", level = DeprecationLevel.HIDDEN)
public fun Configuration.jackson(
    contentType: ContentType = ContentType.Application.Json,
    block: ObjectMapper.() -> Unit = {}
) {
    jackson(contentType, true, block)
}

/**
 * Registers the `application/json` content type to the [ContentNegotiation] plugin using Jackson.
 *
 * You can learn more from [Content negotiation and serialization](https://ktor.io/docs/serialization.html).
 *
 * @param contentType the content type to send with request
 * @param streamRequestBody if set to true, will stream request body, without keeping it whole in memory.
 * This will set `Transfer-Encoding: chunked` header.
 * @param block a configuration block for [ObjectMapper]
 */
public fun Configuration.jackson(
    contentType: ContentType = ContentType.Application.Json,
    streamRequestBody: Boolean = true,
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
    val converter = JacksonConverter(mapper, streamRequestBody)
    register(contentType, converter)
}

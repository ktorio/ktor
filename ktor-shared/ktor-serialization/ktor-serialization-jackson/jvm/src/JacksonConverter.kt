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
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlin.text.Charsets

/**
 * A content converter that uses [Jackson]
 *
 * @param mapper a configured instance of [ObjectMapper]
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
            return TextContent(
                objectMapper.writeValueAsString(value),
                contentType.withCharsetIfNeeded(charset)
            )
        }
        return OutputStreamContent(
            {
                if (charset == Charsets.UTF_8) {
                    /*
                    Jackson internally does special casing on UTF-8, presumably for performance reasons. Thus we pass an
                    InputStream instead of a writer to let Jackson do it's thing.
                     */
                    objectMapper.writeValue(this, value)
                } else {
                    objectMapper.writeValue(this.writer(charset = charset), value)
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

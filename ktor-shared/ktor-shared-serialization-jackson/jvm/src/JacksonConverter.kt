/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.shared.serializaion.jackson

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.core.util.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.shared.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*

public class JacksonConverter(private val objectmapper: ObjectMapper = jacksonObjectMapper()) : ContentConverter {

    private val baseConverter = JacksonBaseConverter(objectmapper)

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): OutgoingContent {
        return OutputStreamContent(
            {
                baseConverter.serialize(charset, typeInfo, value)?.toByteArray()?.let {
                    this.write(it)
                }
            },
            contentType.withCharset(charset)
        )
    }

    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any): SerializedData? =
        baseConverter.serialize(charset, typeInfo, value)

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? =
        baseConverter.deserialize(charset, typeInfo, content)
}

/**
 * Register Jackson converter into [ContentNegotiation] plugin
 */
public fun Configuration.jackson(
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

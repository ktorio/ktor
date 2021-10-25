/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.shared.serializaion.gson

import com.google.gson.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.shared.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

/**
 * GSON converter for [ContentNegotiation] plugin
 */
public class GsonConverter(private val gson: Gson = Gson()) : ContentConverter {

    private val baseConverter = GsonBaseConverter(gson)

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): OutgoingContent? {
        val data = serialize(charset, typeInfo, value).toByteArray()

        return TextContent(
            String(data, charset),
            contentType.withCharset(charset)
        )
    }

    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any): SerializedData =
        baseConverter.serialize(charset, typeInfo, value)

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? =
        baseConverter.deserialize(charset, typeInfo, content)
}

/**
 * Register Gson to [ContentNegotiation] plugin
 */
public fun Configuration.gson(
    contentType: ContentType = ContentType.Application.Json,
    block: GsonBuilder.() -> Unit = {}
) {
    val builder = GsonBuilder()
    builder.apply(block)
    val converter = GsonConverter(builder.create())
    register(contentType, converter)
}

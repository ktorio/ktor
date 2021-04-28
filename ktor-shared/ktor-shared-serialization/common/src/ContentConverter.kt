/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.common.serialization

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.pipeline.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.reflect.*

/**
 * A custom content converted that could be registered in [ContentNegotiation] feature for any particular content type
 * Could provide bi-directional conversion implementation.
 * One of the most typical examples of content converter is a json content converter that provides both
 * serialization and deserialization
 */
public interface ContentConverter {

    /**
     * Serializes a [value] to the specified [contentType] to a [OutgoingContent].
     * This function could ignore value if it is not suitable for conversion and return `null` so in this case
     * other registered converters could be tried or this function could be invoked with other content types
     * it the converted has been registered multiple times with different content types
     *
     * @param charset response charset
     * @param typeInfo response body typeInfo
     * @param contentType to which this data converted has been registered and that matches client's accept header
     * @param value to be converted
     *
     * @return a converted [OutgoingContent] value, or null if [value] isn't suitable for this converter
     */
    public suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): OutgoingContent?

    /**
     * Deserializes [content] to the value of type [typeInfo]
     *
     * @return a converted value (deserialized) or `null` if the context's subject is not suitable for this converter
     */
    public suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any?
}

/**
 * Detect suitable charset for an application call by `Accept` header or fallback to [defaultCharset]
 */
public fun Headers.suitableCharset(defaultCharset: Charset = Charsets.UTF_8): Charset {
    for ((charset, _) in parseAndSortHeader(get(HttpHeaders.AcceptCharset))) when {
        charset == "*" -> return defaultCharset
        Charset.isSupported(charset) -> return Charset.forName(charset)
    }
    return defaultCharset
}

/**
 * Configuration for client and server `ContentNegotiation` feature
 */
public interface Configuration {

    public fun <T : ContentConverter> register(
        contentType: ContentType,
        converter: T,
        configuration: T.() -> Unit = {}
    )
}

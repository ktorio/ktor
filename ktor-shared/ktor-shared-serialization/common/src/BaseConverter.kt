/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.shared.serialization

import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

/**
 * A custom content converted that could be used in [WebSockets] plugin
 * Could provide bi-directional conversion implementation.
 * One of the most typical examples of content converter is a json content converter that provides both
 * serialization and deserialization
 */
public interface BaseConverter {

    /**
     * Serializes a [value] to a [SerializedData].
     *
     * @param charset response charset
     * @param typeInfo response body typeInfo
     * @param value to be converted
     *
     * @return a converted [SerializedData] value
     */
    public suspend fun serialize(
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): SerializedData

    /**
     * Deserializes [content] to the value of type [typeInfo]
     *
     * @return a converted value (deserialized) or `null` if the context's subject is not suitable for this converter
     */
    public suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any?
}

/**
 * Contains serialization result from [BaseConverter] class
 *
 * @param data holds converted value
 * @param dataLength provides information about converted value length in bytes
 */
public data class SerializedData(
    val data: ByteReadChannel,
    val dataLength: Int
) {
    /**
     * Reads serialized object from [data] to a [ByteArray]
     *
     * @return converted [ByteArray] value
     */
    public suspend fun toByteArray(): ByteArray {
        val resultByteArray = ByteArray(dataLength)
        data.readFully(resultByteArray)
        return resultByteArray
    }
}

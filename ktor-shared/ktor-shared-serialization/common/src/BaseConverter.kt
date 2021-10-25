/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.shared.serialization

import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

public interface BaseConverter {
    public suspend fun serialize(
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): SerializedData?

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

public data class SerializedData(
    val data: ByteReadChannel,
    val dataLength: Int
) {
    public suspend fun toByteArray(): ByteArray {
        val resultByteArray = ByteArray(dataLength)
        data.readFully(resultByteArray)
        return resultByteArray
    }
}

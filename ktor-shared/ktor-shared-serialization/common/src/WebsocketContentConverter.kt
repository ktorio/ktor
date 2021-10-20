/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.shared.serialization

import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

public interface WebsocketContentConverter {
    public suspend fun serialize(
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): String

    public suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any?
}

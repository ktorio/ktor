/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.shared.serializaion.gson

import com.google.gson.*
import io.ktor.shared.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*

public class GsonWebsocketConverter(private val gson: Gson = Gson()) : WebsocketContentConverter {
    public override suspend fun serialize(
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): String {
        return gson.toJson(value)
    }

    public override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ): Any? {
        try {
            val reader = content.toInputStream().reader(charset)
            return gson.fromJson(reader, typeInfo.reifiedType)
        } catch (deserializeFailure: JsonSyntaxException) {
            throw JsonConvertException("Illegal json parameter found", deserializeFailure)
        }
    }
}

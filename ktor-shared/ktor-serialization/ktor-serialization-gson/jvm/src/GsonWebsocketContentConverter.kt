/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serializaion.gson

import com.google.gson.*
import io.ktor.http.cio.websocket.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*

/**
 * GSON converter for [WebSockets] plugin
 */
public class GsonWebsocketContentConverter(private val gson: Gson = Gson()) : WebsocketContentConverter {
    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any): Frame {
        return Frame.Text(gson.toJson(value))
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any {
        if(content !is Frame.Text) {
            throw WebsocketConverterNotFoundException("Unsupported frame ${content.frameType.name}")
        }
        if (gson.isExcluded(typeInfo.type)) {
            throw ExcludedTypeGsonException(typeInfo.type)
        }

        try {
            return withContext(Dispatchers.IO) {
                val reader = content.readBytes().inputStream().reader(charset)
                gson.fromJson(reader, typeInfo.reifiedType)
            }
        } catch (deserializeFailure: JsonSyntaxException) {
            throw JsonConvertException("Illegal json parameter found", deserializeFailure)
        }
    }
}

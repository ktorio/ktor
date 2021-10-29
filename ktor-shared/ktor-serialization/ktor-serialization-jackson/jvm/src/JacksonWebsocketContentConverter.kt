/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serializaion.jackson

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.http.cio.websocket.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.text.Charsets

/**
 * Jackson converter for [WebSockets] plugin
 */
public class JacksonWebsocketContentConverter(private val objectmapper: ObjectMapper = jacksonObjectMapper()) : WebsocketContentConverter {
    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any): Frame {
        val outputStream = ByteArrayOutputStream()

        if (charset == Charsets.UTF_8) {
            /*
            Jackson internally does special casing on UTF-8, presumably for performance reasons. Thus we pass an
            InputStream instead of a writer to let Jackson do it's thing.
             */
            objectmapper.writeValue(outputStream, value)
        } else {
            objectmapper.writeValue(outputStream.writer(charset = charset), value)
        }
        return Frame.Binary(true, outputStream.toByteArray())
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any {
        if(content !is Frame.Text && content !is Frame.Binary) {
            throw WebsocketConverterNotFoundException("Unsupported frame ${content.frameType.name}")
        }
        try {
            return withContext(Dispatchers.IO) {
                val reader = content.readBytes().inputStream().reader(charset)
                objectmapper.readValue(reader, objectmapper.constructType(typeInfo.reifiedType))
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

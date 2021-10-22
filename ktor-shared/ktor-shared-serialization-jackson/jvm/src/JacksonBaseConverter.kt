/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.shared.serializaion.jackson

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.shared.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.io.*
import kotlin.text.Charsets

public class JacksonBaseConverter(private val objectmapper: ObjectMapper = jacksonObjectMapper()) : BaseConverter {

    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any): SerializedData? {
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

        return SerializedData(
            ByteReadChannel(outputStream.toByteArray()),
            outputStream.size()
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        try {
            return withContext(Dispatchers.IO) {
                val reader = content.toInputStream().reader(charset)
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

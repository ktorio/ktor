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
import kotlinx.coroutines.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

public open class GsonBaseConverter(private val gson: Gson = Gson()) : BaseConverter {

    override suspend fun serialize(charset: Charset, typeInfo: TypeInfo, value: Any): SerializedData? {
        val text = gson.toJson(value, typeInfo.reifiedType)
            .toByteArray(charset)

        return SerializedData(
            ByteReadChannel(text),
            text.size
        )
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        if (gson.isExcluded(typeInfo.type)) {
            throw ExcludedTypeGsonException(typeInfo.type)
        }

        try {
            return withContext(Dispatchers.IO) {
                val reader = content.toInputStream().reader(charset)
                gson.fromJson(reader, typeInfo.reifiedType)
            }
        } catch (deserializeFailure: JsonSyntaxException) {
            throw JsonConvertException("Illegal json parameter found", deserializeFailure)
        }
    }
}

private fun Gson.isExcluded(type: KClass<*>) =
    excluder().excludeClass(type.java, false)

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExcludedTypeGsonException(
    private val type: KClass<*>
) : Exception("Type ${type.jvmName} is excluded so couldn't be used in receive"),
    CopyableThrowable<ExcludedTypeGsonException> {

    override fun createCopy(): ExcludedTypeGsonException = ExcludedTypeGsonException(type).also {
        it.initCause(this)
    }
}

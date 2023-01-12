/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.http.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import kotlinx.serialization.*

@OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
internal abstract class KotlinxSerializationBase<T>(
    private val format: SerialFormat,
) {
    internal abstract suspend fun serializeContent(parameters: SerializationParameters): T

    internal suspend fun serialize(
        parameters: SerializationParameters,
    ): T {
        val result = format.serializersModule.serializerForTypeInfo(parameters.typeInfo).let {
            parameters.serializer = it
            serializeContent(parameters)
        }

        if (result != null) {
            return result
        }
        val guessedSearchSerializer = guessSerializer(parameters.value, format.serializersModule)
        parameters.serializer = guessedSearchSerializer
        return serializeContent(parameters)
    }
}

internal open class SerializationParameters(
    open val format: SerialFormat,
    open val value: Any?,
    open val typeInfo: TypeInfo,
    open val charset: Charset,
) {
    lateinit var serializer: KSerializer<*>
}

internal class SerializationNegotiationParameters(
    override val format: SerialFormat,
    override val value: Any?,
    override val typeInfo: TypeInfo,
    override val charset: Charset,
    val contentType: ContentType,
) : SerializationParameters(format, value, typeInfo, charset)

package io.ktor.client.features.json.serializer

import io.ktor.client.call.TypeInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.list
import kotlinx.serialization.serializer
import kotlinx.serialization.set
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

internal actual fun serializerForPlatform(type: TypeInfo): KSerializer<*> = type.type.serializer()
package io.ktor.client.features.json

import io.ktor.client.call.TypeInfo
import java.lang.reflect.ParameterizedType
import java.util.*
import kotlin.reflect.full.isSubclassOf

actual fun defaultSerializer(): JsonSerializer {
    val serializers = ServiceLoader.load(JsonSerializer::class.java).toList()
    if (serializers.isEmpty()) error(
        "Fail to find serializer. Consider to add one of the following dependencies: \n" +
                " - ktor-client-gson\n" +
                " - ktor-client-json"
    )

    return serializers.maxBy { it::class.simpleName!! }!!
}

internal actual fun unwrapJsonContent(typeInfo: TypeInfo) : TypeInfo {
    require(typeInfo.type.isSubclassOf(JsonContent::class)) { "typeInfo.type must be type ${JsonContent::class.simpleName}" }
    val reifiedType = (typeInfo.reifiedType as ParameterizedType).actualTypeArguments.first()!!
    val type = (reifiedType as Class<*>).kotlin
    return TypeInfo(type, reifiedType)
}

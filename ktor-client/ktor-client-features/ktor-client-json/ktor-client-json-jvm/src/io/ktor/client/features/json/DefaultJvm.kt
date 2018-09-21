package io.ktor.client.features.json

import io.ktor.client.call.*
import java.lang.reflect.*
import java.util.*

actual fun defaultSerializer(): JsonSerializer {
    val serializers = ServiceLoader.load(JsonSerializer::class.java).toList()
    if (serializers.isEmpty()) error(
        "Fail to find serializer. Consider to add one of the following dependencies: \n" +
                " - ktor-client-gson\n" +
                " - ktor-client-json"
    )

    return serializers.maxBy { it::class.simpleName!! }!!
}

internal actual fun safeUnwrapJsonContent(typeInfo: TypeInfo): TypeInfo? {
    val parameterizedType = typeInfo.reifiedType as? ParameterizedType
    // JSonContent is final so we can avoid subclass check
    return if (parameterizedType?.rawType == JsonContent::class.java){
        val reifiedType = parameterizedType.actualTypeArguments.first()!!
        val type = (reifiedType as Class<*>).kotlin
        TypeInfo(type, reifiedType)
    } else null
}

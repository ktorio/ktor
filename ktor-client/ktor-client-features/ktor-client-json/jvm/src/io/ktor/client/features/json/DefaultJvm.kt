package io.ktor.client.features.json

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

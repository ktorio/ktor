package io.ktor.client.features.json.serializer

import io.ktor.client.features.json.*

private val InitHook = SerializerInitializer

private object SerializerInitializer {
    init {
        serializers.add(KotlinxSerializer())
    }
}

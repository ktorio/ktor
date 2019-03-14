package io.ktor.client.features.json

/**
 * Platform default serializer.
 */
actual fun defaultSerializer(): JsonSerializer =
    serializersStore.first()

@Suppress("KDocMissingDocumentation")
val serializersStore: MutableList<JsonSerializer> = mutableListOf<JsonSerializer>()

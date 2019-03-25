package io.ktor.client.features.json

import io.ktor.util.*

/**
 * Platform default serializer.
 */
actual fun defaultSerializer(): JsonSerializer =
    serializersStore.first()

@Suppress("KDocMissingDocumentation")
@InternalAPI
val serializersStore: MutableList<JsonSerializer> = mutableListOf<JsonSerializer>()

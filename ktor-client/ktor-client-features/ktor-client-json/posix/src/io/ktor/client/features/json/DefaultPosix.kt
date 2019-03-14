package io.ktor.client.features.json

import io.ktor.util.*

/**
 * Platform default serializer.
 */

actual fun defaultSerializer(): JsonSerializer = serializers.first()

@InternalAPI
@Suppress("KDocMissingDocumentation")
val serializers: MutableList<JsonSerializer> by lazy {
    mutableListOf<JsonSerializer>()
}

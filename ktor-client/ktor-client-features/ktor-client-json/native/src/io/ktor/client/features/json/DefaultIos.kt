package io.ktor.client.features.json

import io.ktor.client.features.json.serializer.*

actual fun defaultSerializer(): JsonSerializer = KotlinxSerializer()

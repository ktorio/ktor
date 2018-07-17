package io.ktor.client.features.json

actual fun defaultSerializer(): JsonSerializer = GsonSerializer()

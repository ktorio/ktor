package io.ktor.client.features.json

import io.ktor.client.features.json.serializer.*

/**
 * Platform default serializer.
 *
 * Uses service loader on jvm.
 * Consider to add one of the following dependencies:
 * - ktor-client-gson
 * - ktor-client-json
 */
actual fun defaultSerializer(): JsonSerializer = KotlinxSerializer()

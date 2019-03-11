package io.ktor.client.features.json

import io.ktor.client.call.*
import io.ktor.http.content.*
import kotlinx.io.core.*

/**
 * Client json serializer.
 */
interface JsonSerializer {
    /**
     * Convert data object to [OutgoingContent].
     */
    fun write(data: Any): OutgoingContent

    /**
     * Read content from response using information specified in [type].
     */
    fun read(type: TypeInfo, body: Input): Any
}

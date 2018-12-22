package io.ktor.client.features.json

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.content.*

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
    suspend fun read(type: TypeInfo, response: HttpResponse): Any
}

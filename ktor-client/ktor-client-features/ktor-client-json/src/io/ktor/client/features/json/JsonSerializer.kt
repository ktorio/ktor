package io.ktor.client.features.json

import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.http.content.*

interface JsonSerializer {
    fun write(data: Any): OutgoingContent

    suspend fun read(type: TypeInfo, response: HttpResponse): Any
}
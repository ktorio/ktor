package io.ktor.client.features.json

import io.ktor.content.*
import kotlin.reflect.*

interface JsonSerializer {
    fun write(data: Any): OutgoingContent
    suspend fun read(type: KClass<*>, content: IncomingContent): Any
}
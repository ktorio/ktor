package io.ktor.client.features.json

import kotlin.reflect.*


interface JsonSerializer {
    fun write(data: Any): String
    fun read(type: KClass<*>, data: String): Any
}
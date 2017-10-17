package io.ktor.client.features.json

import com.google.gson.*
import kotlin.reflect.*


class GsonSerializer(block: GsonBuilder.() -> Unit = {}) : JsonSerializer {
    private val backend = GsonBuilder().apply(block).create()

    override fun write(data: Any): String = backend.toJson(data)

    override fun read(type: KClass<*>, data: String): Any = backend.fromJson(data, type.java)
}
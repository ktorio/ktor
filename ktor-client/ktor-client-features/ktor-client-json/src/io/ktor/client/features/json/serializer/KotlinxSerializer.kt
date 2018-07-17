package io.ktor.client.features.json.serializer

import io.ktor.client.call.*
import io.ktor.client.features.json.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.*
import kotlin.reflect.*

typealias ReadMapper<T> = (JsonElement) -> T
typealias WriteMapper<T> = (Any) -> JsonElement

class KotlinxSerializer : JsonSerializer {
    private val readMappers = mutableMapOf<KClass<*>, ReadMapper<*>>()
    private val writeMappers = mutableMapOf<KClass<*>, WriteMapper<*>>()

    fun <T : Any> setReadMapper(type: KClass<T>, block: ReadMapper<T>) {
        readMappers[type] = block
    }

    fun <T : Any> setWriteMapper(type: KClass<T>, block: WriteMapper<T>) {
        writeMappers[type] = block
    }

    override fun write(data: Any): OutgoingContent {
        val mapper = writeMappers[data::class]!!
        return TextContent(mapper(data).toString(), ContentType.Application.Json)
    }

    override suspend fun read(info: TypeInfo, response: HttpResponse): Any {
        val mapper = readMappers[info.type]!!
        val text = response.readText()
        return mapper(JsonTreeParser(text).readFully())!!
    }
}
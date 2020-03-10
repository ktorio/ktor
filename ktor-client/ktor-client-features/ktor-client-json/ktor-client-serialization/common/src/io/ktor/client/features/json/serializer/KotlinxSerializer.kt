/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json.serializer

import io.ktor.client.call.*
import io.ktor.client.features.json.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.json.*
import kotlinx.serialization.list
import kotlinx.serialization.set
import kotlin.reflect.*

/**
 * A [JsonSerializer] implemented for kotlinx [Serializable] classes.
 */
@OptIn(
    ImplicitReflectionSerializer::class, UnstableDefault::class
)
class KotlinxSerializer(
    private val json: Json = Json.plain
) : JsonSerializer {

    /**
     * Set mapping from [type] to generated [KSerializer].
     */
    @Deprecated("[setMapper] is obsolete with 1.3.50 `typeOf` feature", level = DeprecationLevel.WARNING)
    fun <T : Any> setMapper(type: KClass<T>, serializer: KSerializer<T>) {
    }

    /**
     * Set mapping from [type] to generated [KSerializer].
     */
    @Deprecated("[setListMapper] is obsolete with 1.3.50 `typeOf` feature", level = DeprecationLevel.WARNING)
    fun <T : Any> setListMapper(type: KClass<T>, serializer: KSerializer<T>) {
    }

    /** Set the mapping from [T] to [mapper]. */
    @Suppress("UNUSED_PARAMETER", "unused")
    @Deprecated("[register] is obsolete with 1.3.50 `typeOf` feature", level = DeprecationLevel.WARNING)
    inline fun <reified T : Any> register(mapper: KSerializer<T>) {
    }

    /** Set the mapping from [List<T>] to [mapper]. */
    @Suppress("UNUSED_PARAMETER", "unused")
    @Deprecated("[register] is obsolete with 1.3.50 `typeOf` feature", level = DeprecationLevel.WARNING)
    inline fun <reified T : Any> registerList(mapper: KSerializer<T>) {
    }

    /**
     * Set the mapping from [T] to it's [KSerializer]. This method only works for non-parameterized types.
     */
    @Suppress("unused")
    @Deprecated("[register] is obsolete with 1.3.50 `typeOf` feature", level = DeprecationLevel.WARNING)
    inline fun <reified T : Any> register() {
    }

    /**
     * Set the mapping from [List<T>] to it's [KSerializer]. This method only works for non-parameterized types.
     */
    @Deprecated("[register] is obsolete with 1.3.50 `typeOf` feature", level = DeprecationLevel.WARNING)
    @Suppress("unused")
    inline fun <reified T : Any> registerList() {
    }

    override fun write(data: Any, contentType: ContentType): OutgoingContent {
        @Suppress("UNCHECKED_CAST")
        val content = json.stringify(buildSerializer(data) as KSerializer<Any>, data)
        return TextContent(content, contentType)
    }

    override fun read(type: TypeInfo, body: Input): Any {
        val text = body.readText()
        val mapper = type.kotlinType?.let { serializer(it) } ?: type.type.serializer()
        return json.parse(mapper, text)!!
    }
}

@Suppress("UNCHECKED_CAST")
@OptIn(ImplicitReflectionSerializer::class)
private fun buildSerializer(value: Any): KSerializer<*> = when (value) {
    is JsonElement -> JsonElementSerializer
    is List<*> -> value.elementSerializer().list
    is Array<*> -> value.firstOrNull()?.let { buildSerializer(it) } ?: String.serializer().list
    is Set<*> -> value.elementSerializer().set
    is Map<*, *> -> {
        val keySerializer = value.keys.elementSerializer() as KSerializer<Any>
        val valueSerializer = value.values.elementSerializer() as KSerializer<Any>
        MapSerializer(keySerializer, valueSerializer)
    }
    else -> value::class.serializer()
}

@OptIn(ImplicitReflectionSerializer::class)
private fun Collection<*>.elementSerializer(): KSerializer<*> {
    @Suppress("DEPRECATION_ERROR")
    val serializers = filterNotNull().map { buildSerializer(it) }.distinctBy { it.descriptor.name }

    if (serializers.size > 1) {
        @Suppress("DEPRECATION_ERROR")
        error(
            "Serializing collections of different element types is not yet supported. " +
                "Selected serializers: ${serializers.map { it.descriptor.name }}"
        )
    }

    val selected = serializers.singleOrNull() ?: String.serializer()

    if (selected.descriptor.isNullable) {
        return selected
    }

    @Suppress("UNCHECKED_CAST")
    selected as KSerializer<Any>

    if (any { it == null }) {
        return selected.nullable
    }

    return selected
}

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import com.squareup.moshi.*
import io.ktor.client.call.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import okio.*
import kotlin.reflect.*

/**
 * [JsonSerializer] using [Moshi] as backend.
 */
@ExperimentalStdlibApi
class MoshiSerializer(block: Moshi.Builder.() -> Unit = {}) : JsonSerializer {
    private val backend: Moshi = Moshi.Builder().apply(block).build()

    override fun write(data: Any, contentType: ContentType): OutgoingContent =
        TextContent(backend.adapter<Any>(data.javaClass).toJson(data), contentType)

    override fun read(type: TypeInfo, body: Input): Any {
        val javaType = type.kotlinType?.javaType ?: error("No KType information available for $type")
        return backend.adapter<Any>(javaType).fromJson(body.asStream().source().buffer()) ?: error("Json deserialization returned null")
    }
}

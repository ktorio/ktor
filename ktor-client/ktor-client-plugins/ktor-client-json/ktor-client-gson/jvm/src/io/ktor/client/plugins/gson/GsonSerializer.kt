/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.plugins.gson

import com.google.gson.*
import io.ktor.client.plugins.json.JsonSerializer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.*

/**
 * [JsonSerializer] using [Gson] as backend.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.gson.GsonSerializer)
 */
@Suppress("ktlint:standard:max-line-length")
@Deprecated(
    "Please use ContentNegotiation plugin and its converters: https://ktor.io/docs/migration-to-20x.html#serialization-client",
    level = DeprecationLevel.ERROR
)
public class GsonSerializer(block: GsonBuilder.() -> Unit = {}) : JsonSerializer {
    private val backend: Gson = GsonBuilder().apply(block).create()

    override fun write(data: Any, contentType: ContentType): OutgoingContent =
        TextContent(backend.toJson(data), contentType)

    override fun read(type: TypeInfo, body: Input): Any {
        val text = body.readText()
        return backend.fromJson(text, type.reifiedType)
    }
}

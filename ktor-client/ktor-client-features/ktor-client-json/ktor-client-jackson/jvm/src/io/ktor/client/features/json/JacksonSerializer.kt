/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.io.core.*

class JacksonSerializer(private val backend: ObjectMapper) : JsonSerializer {
    constructor(block: ObjectMapper.() -> Unit = {}) : this(jacksonObjectMapper().apply(block))

    override fun write(data: Any, contentType: ContentType): OutgoingContent =
        TextContent(backend.writeValueAsString(data), contentType)

    override fun read(type: TypeInfo, body: Input): Any {
        return backend.readValue(body.readText(), backend.typeFactory.constructType(type.reifiedType))
    }
}

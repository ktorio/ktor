/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.*

public class JacksonSerializer(
    jackson: ObjectMapper = jacksonObjectMapper(),
    block: ObjectMapper.() -> Unit = {}
) : JsonSerializer {
    private val backend = jackson.apply(block)

    override fun write(data: Any, contentType: ContentType): OutgoingContent =
        TextContent(backend.writeValueAsString(data), contentType)

    override fun read(type: TypeInfo, body: Input): Any {
        return backend.readValue(body.readText(), backend.typeFactory.constructType(type.reifiedType))
    }
}

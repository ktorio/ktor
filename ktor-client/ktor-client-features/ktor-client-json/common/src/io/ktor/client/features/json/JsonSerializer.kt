/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json

import io.ktor.client.call.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.core.*
import kotlin.reflect.*

/**
 * Client json serializer.
 */
public interface JsonSerializer {

    /**
     * Convert data object to [OutgoingContent].
     */
    public fun write(payload: Any, contentType: ContentType, payloadType: KType?): OutgoingContent =
        write(payload, contentType)

    /**
     * Convert data object to [OutgoingContent].
     */
    public fun write(data: Any, contentType: ContentType): OutgoingContent

    /**
     * Convert data object to [OutgoingContent].
     */
    public fun write(data: Any): OutgoingContent = write(data, ContentType.Application.Json, null)

    /**
     * Read content from response using information specified in [type].
     */
    public fun read(type: TypeInfo, body: Input): Any
}

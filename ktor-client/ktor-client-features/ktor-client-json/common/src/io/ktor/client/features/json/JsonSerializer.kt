/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.json

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.*
import io.ktor.client.call.TypeInfo as DeprecatedTypeInfo

/**
 * Client json serializer.
 */
public interface JsonSerializer {
    /**
     * Convert data object to [OutgoingContent].
     */
    public fun write(data: Any, contentType: ContentType): OutgoingContent

    /**
     * Convert data object to [OutgoingContent].
     */
    public fun write(data: Any): OutgoingContent = write(data, ContentType.Application.Json)

    /**
     * Read content from response using information specified in [type].
     */
    @Deprecated("Please use overload with io.ktor.util.reflect.TypeInfo parameter")
    public fun read(type: DeprecatedTypeInfo, body: Input): Any = read(type as TypeInfo, body)

    /**
     * Read content from response using information specified in [type].
     */
    public fun read(type: TypeInfo, body: Input): Any =
        read(DeprecatedTypeInfo(type.type, type.reifiedType, type.kotlinType), body)
}

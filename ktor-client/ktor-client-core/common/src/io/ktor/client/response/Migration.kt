/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("KDocMissingDocumentation")

package io.ktor.client.response

import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Deprecated(
    "Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpStatement", "io.ktor.client.statement.*")
)
public class HttpResponse : CoroutineScope, HttpMessage {
    override val coroutineContext: CoroutineContext
        get() = error("Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.")
    override val headers: Headers
        get() = error("Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.")
}

@Suppress("DEPRECATION_ERROR", "unused", "UNUSED_PARAMETER", "RedundantSuspendModifier")
@Deprecated(
    "Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.",
    level = DeprecationLevel.ERROR
)
public suspend fun HttpResponse.readText(charset: Charset? = null): String {
    error("Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.")
}

/**
 * Exactly reads [count] bytes of the [HttpResponse.content].
 */
@Deprecated(
    "Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.",
    level = DeprecationLevel.ERROR
)
@Suppress("DEPRECATION_ERROR", "unused", "UNUSED_PARAMETER", "RedundantSuspendModifier")
public suspend fun HttpResponse.readBytes(count: Int): ByteArray {
    error("Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.")
}

/**
 * Reads the whole [HttpResponse.content] if Content-Length was specified.
 * Otherwise it just reads one byte.
 */
@Deprecated(
    "Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.",
    level = DeprecationLevel.ERROR
)
@Suppress("DEPRECATION_ERROR", "unused", "UNUSED_PARAMETER", "RedundantSuspendModifier")
public suspend fun HttpResponse.readBytes(): ByteArray {
    error("Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.")
}

/**
 * Efficiently discards the remaining bytes of [HttpResponse.content].
 */
@Deprecated(
    "Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.",
    level = DeprecationLevel.ERROR
)
@Suppress("DEPRECATION_ERROR", "unused", "UNUSED_PARAMETER", "RedundantSuspendModifier")
public suspend fun HttpResponse.discardRemaining() {
    error("Unbound streaming [HttpResponse] is deprecated. Consider using [HttpStatement] instead.")
}

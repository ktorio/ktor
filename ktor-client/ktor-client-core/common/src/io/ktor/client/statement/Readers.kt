/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.utils.io.*

/**
 * Reads exactly [count] bytes of the [HttpResponse.body].
 */
@OptIn(InternalAPI::class)
public suspend fun HttpResponse.readBytes(count: Int): ByteArray = body.readBytes(count)

/**
 * Reads the whole [HttpResponse.body] if `Content-Length` is specified.
 * Otherwise, it just reads one byte.
 */

@OptIn(InternalAPI::class)
public suspend fun HttpResponse.readBytes(): ByteArray = body.readBytes()

/**
 * Efficiently discards the remaining bytes of [HttpResponse.body].
 */
@OptIn(InternalAPI::class)
public suspend fun HttpResponse.discardRemaining() {
    body.discard()
}

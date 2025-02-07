/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.request

import io.ktor.server.application.*
import java.io.*

/**
 * Receives stream content for this call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.request.receiveStream)
 *
 * @return instance of [InputStream] to read incoming bytes for this call.
 * @throws ContentTransformationException when content cannot be transformed to the [InputStream].
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun ApplicationCall.receiveStream(): InputStream = receive()

@PublishedApi
internal actual val DEFAULT_FORM_FIELD_LIMIT: Long
    get() = System.getProperty("io.ktor.server.request.formFieldLimit")?.toLongOrNull() ?: (50 * 1024 * 1024L)

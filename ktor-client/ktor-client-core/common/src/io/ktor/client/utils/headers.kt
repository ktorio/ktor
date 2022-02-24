/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.utils

import io.ktor.http.*
import io.ktor.util.*

/**
 * Builds an instance of [Headers] using the [block] function.
 */
public fun buildHeaders(block: HeadersBuilder.() -> Unit = {}): Headers =
    HeadersBuilder().apply(block).build()

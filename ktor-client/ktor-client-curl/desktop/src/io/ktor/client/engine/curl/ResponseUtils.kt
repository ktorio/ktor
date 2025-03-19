/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.curl

import io.ktor.http.HeadersBuilder
import io.ktor.http.cio.*

internal fun HttpHeadersMap.toBuilder(): HeadersBuilder {
    val builder = HeadersBuilder()

    for (offset in offsets()) {
        val key = nameAtOffset(offset).toString()
        val value = valueAtOffset(offset).toString()

        builder.append(key, value)
    }

    return builder
}

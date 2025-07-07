/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.http.HttpMethod

internal actual fun checkContentLength(contentLength: Long?, bodySize: Long, method: HttpMethod) {
    if (contentLength == null || contentLength < 0 || method == HttpMethod.Head) return

    check(contentLength == bodySize) {
        "Content-Length mismatch: expected $contentLength bytes, but received $bodySize bytes"
    }
}

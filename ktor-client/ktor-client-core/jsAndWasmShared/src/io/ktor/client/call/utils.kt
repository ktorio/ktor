/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.http.HttpMethod

internal actual fun checkContentLength(contentLength: Long?, bodySize: Long, method: HttpMethod) {
    // Content-Length is unreliable in the browser, so we omit this check
}

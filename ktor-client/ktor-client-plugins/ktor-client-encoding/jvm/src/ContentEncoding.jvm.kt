/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.plugins.compression

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

/**
 * Always decode content when the header is present.
 */
internal actual fun shouldDecode(response: HttpResponse): Boolean =
    response.headers[HttpHeaders.ContentEncoding] != null

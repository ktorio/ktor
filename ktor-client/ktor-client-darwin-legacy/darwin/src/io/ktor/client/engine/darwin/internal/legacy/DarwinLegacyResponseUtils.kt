/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal.legacy

import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.Attributes
import io.ktor.utils.io.InternalAPI
import platform.Foundation.*

@OptIn(InternalAPI::class)
internal fun NSHTTPURLResponse.readHeaders(method: HttpMethod, attributes: Attributes): Headers = buildHeaders {
    allHeaderFields.mapKeys { (key, value) -> append(key as String, value as String) }
    dropCompressionHeaders(method, attributes)
}

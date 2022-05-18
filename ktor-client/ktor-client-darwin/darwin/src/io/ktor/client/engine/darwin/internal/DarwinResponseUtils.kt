/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.client.utils.*
import io.ktor.http.*
import platform.Foundation.*

internal fun NSHTTPURLResponse.readHeaders(): Headers = buildHeaders {
    allHeaderFields.mapKeys { (key, value) -> append(key as String, value as String) }
}

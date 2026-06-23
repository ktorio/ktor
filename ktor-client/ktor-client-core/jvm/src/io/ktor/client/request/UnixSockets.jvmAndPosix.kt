/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.request

import io.ktor.utils.io.*

// The function has been move to `common` code, but because file name is an ABI on JVM,
// we need to keep a duplicate function here
@OptIn(InternalAPI::class)
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
public fun HttpRequestBuilder.unixSocket(path: String) {
    setCapability(UnixSocketCapability, UnixSocketSettings(path))
}

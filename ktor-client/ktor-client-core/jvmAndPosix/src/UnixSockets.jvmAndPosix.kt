/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.request

import io.ktor.utils.io.*

/**
 * Sets the path to the Unix domain socket file.
 *
 * ```kotlin
 * val client = client.get("http://localhost/api") {
 *    unixSocket("/var/run/docker.sock")
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.request.unixSocket)
 */
@OptIn(InternalAPI::class)
public fun HttpRequestBuilder.unixSocket(path: String) {
    setCapability(UnixSocketCapability, UnixSocketSettings(path))
}

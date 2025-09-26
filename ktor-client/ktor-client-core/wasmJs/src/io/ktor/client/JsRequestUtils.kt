/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.fetch.*
import io.ktor.client.request.*
import io.ktor.util.*

/**
 * Configures the HTTP request with custom options using the provided [block].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.fetchOptions)
 *
 * @param block A lambda with a [RequestInit] receiver used to set additional options such as headers, body, or method.
 */
public fun HttpRequestBuilder.fetchOptions(block: RequestInit.() -> Unit) {
    attributes.put(FetchOptions.key, FetchOptions(block))
}

internal class FetchOptions(val requestInit: RequestInit.() -> Unit) {
    companion object {
        val key = AttributeKey<FetchOptions>("FetchOptions")
    }
}

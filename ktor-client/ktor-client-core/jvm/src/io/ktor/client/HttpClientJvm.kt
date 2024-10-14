/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client

import io.ktor.client.engine.*
import io.ktor.utils.io.*
import java.util.*

/**
 * Constructs an asynchronous [HttpClient] using optional [block] for configuring this client.
 *
 * The [HttpClientEngine] is selected from the dependencies using [ServiceLoader].
 * The first found implementation that provides [HttpClientEngineContainer] service implementation is used.
 * An exception is thrown if no implementations found.
 *
 * See https://ktor.io/docs/http-client-engines.html
 */
@KtorDsl
public actual fun HttpClient(
    block: HttpClientConfig<*>.() -> Unit
): HttpClient = HttpClient(FACTORY, block)

/**
 * A container is searched across dependencies using [ServiceLoader] to find client implementations.
 * An implementation of this interface provides HTTP client [factory] and only used
 * to find the default client engine
 * when [HttpClient] function is called with no particular client implementation specified
 *
 * @property factory that produces HTTP client instances
 */
public interface HttpClientEngineContainer {
    public val factory: HttpClientEngineFactory<*>
}

// ServiceLoader should use specific call convention to be optimized by R8 on Android:
// `ServiceLoader.load(X.class, X.class.getClassLoader()).iterator()`
// source:
// https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java
private val engines = Iterable {
    ServiceLoader.load(
        HttpClientEngineContainer::class.java,
        HttpClientEngineContainer::class.java.classLoader
    ).iterator()
}.toList()

private val FACTORY = engines.firstOrNull()?.factory ?: error(
    "Failed to find HTTP client engine implementation: consider adding client engine dependency. " +
        "See https://ktor.io/docs/http-client-engines.html"
)

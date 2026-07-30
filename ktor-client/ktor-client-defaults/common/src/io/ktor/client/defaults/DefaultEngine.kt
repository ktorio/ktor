/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.defaults

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.utils.io.ExperimentalKtorApi

/**
 * The engine curated by this module for the current platform.
 *
 * Unlike the no-argument [HttpClient] function, which discovers an engine at runtime
 * (via `ServiceLoader` on JVM or the global engine registry on other platforms) and therefore
 * depends on what else is present on the classpath, this property is resolved at compile time
 * and always points to the engine this module selected for the target:
 *
 * | Target                        | Engine   |
 * |-------------------------------|----------|
 * | JVM                           | `OkHttp` |
 * | Linux                         | `Curl`   |
 * | Windows (MinGW)               | `WinHttp`|
 * | Darwin (iOS/macOS/tvOS/watchOS) | `Darwin` |
 * | Android Native                | `CIO`    |
 * | JS and Wasm/JS                | `Js`     |
 *
 * Prefer it over `HttpClient()` when the engine must be deterministic:
 * ```kotlin
 * val client = HttpClient(DefaultEngine)
 * ```
 *
 * Note that the engine chosen for a platform may change in future releases.
 */
@ExperimentalKtorApi
public expect val DefaultEngine: HttpClientEngineFactory<*>

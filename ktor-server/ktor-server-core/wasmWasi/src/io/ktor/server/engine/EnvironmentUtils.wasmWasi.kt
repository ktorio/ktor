/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

internal actual fun getKtorEnvironmentProperties(): List<Pair<String, String>> = emptyList()

internal actual fun getEnvironmentProperty(key: String): String? = null

internal actual fun setEnvironmentProperty(key: String, value: String) {
    // impossible in WASM-WASI
}

internal actual fun clearEnvironmentProperty(key: String) {
    // impossible in WASM-WASI
}

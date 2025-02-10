/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.cinterop.toKString
import platform.posix.*
import io.ktor.server.engine.interop.environ as interopEnviron

internal actual fun setEnvironmentProperty(key: String, value: String) {
    setenv(key, value, 0)
}

internal actual fun clearEnvironmentProperty(key: String) {
    unsetenv(key)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getKtorEnvironmentProperties(): List<Pair<String, String>> = buildList {
    var index = 0
    // `platform.posix` also contains `environ` for some targets
    val env = interopEnviron ?: return@buildList
    while (env[index] != null) {
        val keyValue = env[index]?.toKString() ?: continue
        index++
        if (keyValue.startsWith("ktor.")) {
            val (key, value) = keyValue.splitPair('=') ?: continue
            add(key to value)
        }
    }
}

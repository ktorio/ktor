/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import kotlinx.cinterop.*
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun setEnvironmentProperty(key: String, value: String): Unit = memScoped {
    SetEnvironmentVariable!!(key.wcstr.ptr, value.wcstr.ptr)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun clearEnvironmentProperty(key: String): Unit = memScoped {
    SetEnvironmentVariable!!(key.wcstr.ptr, null)
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun getKtorEnvironmentProperties(): List<Pair<String, String>> = buildList {
    val rawEnv: LPWCH = GetEnvironmentStringsW() ?: return@buildList
    try {
        var current: CPointer<WCHARVar>? = rawEnv
        while (current != null) {
            val keyValue = current.toKString()
            if (keyValue.isEmpty()) break
            current += keyValue.length + 1

            val (key, value) = keyValue.splitPair('=') ?: continue
            if (key.startsWith("ktor.")) {
                add(key to value)
            }
        }
    } finally {
        FreeEnvironmentStringsW(rawEnv)
    }
}

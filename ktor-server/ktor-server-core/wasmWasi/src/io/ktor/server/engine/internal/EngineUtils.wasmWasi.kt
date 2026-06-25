/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

internal actual fun escapeHostname(value: String): String {
    return "127.0.0.1"
}

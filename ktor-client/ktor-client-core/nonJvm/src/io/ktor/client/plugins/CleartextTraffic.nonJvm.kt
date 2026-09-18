/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

/**
 * Non-JVM platforms are assumed to permit cleartext traffic.
 */
internal actual fun isCleartextTrafficPermitted(hostname: String): Boolean = true

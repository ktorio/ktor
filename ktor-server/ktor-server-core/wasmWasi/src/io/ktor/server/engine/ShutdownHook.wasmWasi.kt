/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

// it's not possible to set up hook
internal actual val SHUTDOWN_HOOK_ENABLED = false

internal actual fun EmbeddedServer<*, *>.platformAddShutdownHook(stop: () -> Unit) {}

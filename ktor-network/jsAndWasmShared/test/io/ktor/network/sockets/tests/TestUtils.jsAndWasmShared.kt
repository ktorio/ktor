/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

internal actual fun Any.supportsUnixDomainSockets(): Boolean = false

internal actual fun Throwable.isPosixException(): Boolean = false

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import io.ktor.utils.io.errors.*

internal actual fun Any.supportsUnixDomainSockets(): Boolean = true

internal actual fun Throwable.isPosixException(): Boolean = this is PosixException

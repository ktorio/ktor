/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.util.isAFUnixSupported

internal actual fun isUnixSocketSupported(): Boolean = isAFUnixSupported

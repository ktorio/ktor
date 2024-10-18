/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets.tests

import kotlin.experimental.*

@OptIn(ExperimentalNativeApi::class)
internal actual fun Any.supportsUnixDomainSockets(): Boolean = when (Platform.osFamily) {
    OsFamily.MACOSX, OsFamily.LINUX -> true
    else -> false
}

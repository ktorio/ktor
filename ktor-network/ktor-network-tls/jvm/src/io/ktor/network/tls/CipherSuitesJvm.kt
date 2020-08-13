/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.platform.*

internal actual fun CipherSuite.isSupported(): Boolean = when (platformVersion.major) {
    "1.8.0" -> platformVersion.minor >= 161 || keyStrength <= 128
    else -> keyStrength <= 128
}

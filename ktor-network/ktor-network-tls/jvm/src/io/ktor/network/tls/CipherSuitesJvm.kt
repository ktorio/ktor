/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.platform.*

internal actual fun CipherSuite.isSupported(): Boolean = when (platformVersion.major) {
    "1.8.0" -> platformVersion.minor >= 161 || keyStrength <= 128
    "1.7.0" -> platformVersion.minor >= 171 || keyStrength <= 128
    "1.6.0" -> platformVersion.minor >= 181 || keyStrength <= 128
    else -> true
}

public actual typealias TlsException = javax.net.ssl.SSLException
public actual typealias TlsPeerUnverifiedException = javax.net.ssl.SSLPeerUnverifiedException

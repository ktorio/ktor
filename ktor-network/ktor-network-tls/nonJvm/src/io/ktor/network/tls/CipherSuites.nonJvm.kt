/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import kotlinx.io.IOException

internal actual fun CipherSuite.isSupported(): Boolean = false

public actual open class TlsException : IOException {
    public actual constructor(message: String) : super(message)
    public actual constructor(message: String, cause: Throwable?) : super(message, cause)
}

public actual class TlsPeerUnverifiedException actual constructor(message: String) : TlsException(message)

// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*

public actual class TLSConfig(
    public actual val isClient: Boolean,
    public actual val serverName: String?,
    public actual val authentication: TLSAuthenticationConfig?,
    public actual val verification: TLSVerificationConfig?,
) : Closeable {
    override fun close() {
        //NOOP
    }
}

public actual class TLSAuthenticationConfig(
    public val certificate: PKCS12Certificate?,
    public val privateKeyPassword: () -> CharArray
)

public actual class TLSVerificationConfig(
    public val certificate: PKCS12Certificate?,
)

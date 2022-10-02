/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

import io.ktor.utils.io.core.*

//TODO: make it closeable
public expect class TLSConfig: Closeable {
    public val isClient: Boolean
    public val serverName: String?
    public val authentication: TLSAuthenticationConfig?
    public val verification: TLSVerificationConfig?
}

public expect class TLSAuthenticationConfig

public expect class TLSVerificationConfig

public fun TLSConfig(
    isClient: Boolean = true,
    block: TLSConfigBuilder.() -> Unit
): TLSConfig = TLSConfigBuilder(isClient).apply(block).build()

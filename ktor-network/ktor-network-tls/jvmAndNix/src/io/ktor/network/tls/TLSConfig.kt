/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

//TODO: make it closeable
public expect class TLSConfig {
    public val isClient: Boolean
    public val serverName: String?
    public val authentication: TLSAuthenticationConfig?
}

public expect class TLSAuthenticationConfig

public fun TLSConfig(
    isClient: Boolean = true,
    block: TLSConfigBuilder.() -> Unit
): TLSConfig = TLSConfigBuilder(isClient).apply(block).build()

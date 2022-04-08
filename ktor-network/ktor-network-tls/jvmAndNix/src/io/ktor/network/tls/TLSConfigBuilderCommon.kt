/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

/**
 * [TLSConfig] builder.
 */
//TODO: add verification - alternative to JVM trust manager
public expect class TLSConfigBuilder(isClient: Boolean = true) {
    /**
     * Custom server name for TLS server name extension.
     * See also: https://en.wikipedia.org/wiki/Server_Name_Indication
     */
    //TODO: rename to peerName?
    public var serverName: String?

    //if used, on JVM, SSLEngine will be used, instead of handwritten implementation
    //supported on Linux native
    //`certificates` on JVM should be empty
    public fun authentication(privateKeyPassword: () -> CharArray, block: TLSAuthenticationConfigBuilder.() -> Unit)

    /**
     * Append config from [other] builder.
     */
    public fun takeFrom(other: TLSConfigBuilder)

    /**
     * Create [TLSConfig].
     */
    public fun build(): TLSConfig
}

public expect class TLSAuthenticationConfigBuilder(
    privateKeyPassword: () -> CharArray
) {
    public fun pkcs12Certificate(certificatePath: String, certificatePassword: (() -> CharArray)? = null)
    public fun build(): TLSAuthenticationConfig
}

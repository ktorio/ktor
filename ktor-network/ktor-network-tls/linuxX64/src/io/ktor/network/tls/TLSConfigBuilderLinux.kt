/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

/**
 * [TLSConfig] builder.
 */
public actual class TLSConfigBuilder actual constructor(private val isClient: Boolean) {
    private var authenticationBuilder: TLSAuthenticationConfigBuilder? = null
    private var verificationBuilder: TLSVerificationConfigBuilder? = null

    /**
     * Custom server name for TLS server name extension.
     * See also: https://en.wikipedia.org/wiki/Server_Name_Indication
     */
    public actual var serverName: String? = null

    public actual fun authentication(
        privateKeyPassword: () -> CharArray,
        block: TLSAuthenticationConfigBuilder.() -> Unit
    ) {
        authenticationBuilder = TLSAuthenticationConfigBuilder(privateKeyPassword).apply(block)
    }

    public actual fun verification(
        block: TLSVerificationConfigBuilder.() -> Unit
    ) {
        verificationBuilder = TLSVerificationConfigBuilder().apply(block)
    }

    public actual fun takeFrom(other: TLSConfigBuilder) {
        serverName = other.serverName
        authenticationBuilder = other.authenticationBuilder
    }

    /**
     * Create [TLSConfig].
     */
    public actual fun build(): TLSConfig = TLSConfig(
        isClient = isClient,
        serverName = serverName,
        authentication = authenticationBuilder?.build(),
        verification = verificationBuilder?.build()
    )
}

public actual class TLSAuthenticationConfigBuilder actual constructor(
    private val privateKeyPassword: () -> CharArray
) {
    private var certificate: PKCS12Certificate? = null

    public actual fun pkcs12Certificate(certificatePath: String, certificatePassword: (() -> CharArray)?) {
        certificate = PKCS12Certificate(certificatePath, certificatePassword)
    }

    public actual fun build(): TLSAuthenticationConfig = TLSAuthenticationConfig(
        certificate,
        privateKeyPassword
    )
}

public actual class TLSVerificationConfigBuilder {
    private var certificate: PKCS12Certificate? = null

    public actual fun pkcs12Certificate(certificatePath: String, certificatePassword: (() -> CharArray)?) {
        certificate = PKCS12Certificate(certificatePath, certificatePassword)
    }

    public actual fun build(): TLSVerificationConfig = TLSVerificationConfig(
        certificate
    )
}

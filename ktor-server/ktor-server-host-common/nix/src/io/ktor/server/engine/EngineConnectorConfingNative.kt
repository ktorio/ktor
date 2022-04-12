/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

/**
 * Mutable implementation of EngineSSLConnectorConfig for building connectors programmatically
 */
public actual class EngineSSLConnectorBuilder : EngineConnectorBuilder(ConnectorType.HTTPS), EngineSSLConnectorConfig {
    override var port: Int = 443
    actual override var authentication: AuthenticationConfigBuilder? = null
    actual override var verification: VerificationConfigBuilder? = null

    public actual fun authentication(
        privateKeyPassword: () -> CharArray,
        block: AuthenticationConfigBuilder.() -> Unit
    ) {
        this.authentication = AuthenticationConfigBuilder(privateKeyPassword).apply(block)
    }

    public actual fun verification(block: VerificationConfigBuilder.() -> Unit) {
        this.verification = VerificationConfigBuilder().apply(block)
    }

    public actual class AuthenticationConfigBuilder actual constructor(
        public override val privateKeyPassword: () -> CharArray,
    ) : EngineSSLConnectorConfig.AuthenticationConfig {
        override var pkcs12Certificate: PKCS12Certificate? = null

        public actual fun pkcs12Certificate(
            certificatePath: String,
            certificatePasswordProvider: (() -> CharArray)?
        ) {
            pkcs12Certificate = PKCS12Certificate(certificatePath, certificatePasswordProvider)
        }
    }

    public actual class VerificationConfigBuilder : EngineSSLConnectorConfig.VerificationConfig {
        override var pkcs12Certificate: PKCS12Certificate? = null

        public actual fun pkcs12Certificate(
            certificatePath: String,
            certificatePasswordProvider: (() -> CharArray)?
        ) {
            pkcs12Certificate = PKCS12Certificate(certificatePath, certificatePasswordProvider)
        }
    }
}

/**
 * Represents an SSL connector configuration.
 */
public actual interface EngineSSLConnectorConfig : EngineConnectorConfig {

    public actual val authentication: AuthenticationConfig?
    public actual val verification: VerificationConfig?

    public actual interface AuthenticationConfig {

        /**
         * Private key password provider
         */
        public actual val privateKeyPassword: () -> CharArray

        public val pkcs12Certificate: PKCS12Certificate?
    }

    public actual interface VerificationConfig {
        public val pkcs12Certificate: PKCS12Certificate?
    }
}

public class PKCS12Certificate(
    public val path: String,
    public val passwordProvider: (() -> CharArray)?
)

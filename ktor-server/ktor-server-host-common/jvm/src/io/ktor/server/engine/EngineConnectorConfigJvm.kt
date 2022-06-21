/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import java.io.*
import java.security.*

/**
 * Adds a secure connector to this engine environment
 */
public inline fun ApplicationEngineEnvironmentBuilder.sslConnector(
    keyStore: KeyStore,
    keyAlias: String,
    noinline keyStorePassword: () -> CharArray,
    noinline privateKeyPassword: () -> CharArray,
    builder: EngineSSLConnectorBuilder.() -> Unit
) {
    connectors.add(EngineSSLConnectorBuilder(keyStore, keyAlias, keyStorePassword, privateKeyPassword).apply(builder))
}

/**
 * Mutable implementation of EngineSSLConnectorConfig for building connectors programmatically
 */
public actual class EngineSSLConnectorBuilder actual constructor() :
    EngineConnectorBuilder(ConnectorType.HTTPS), EngineSSLConnectorConfig {
    public constructor(
        keyStore: KeyStore,
        keyAlias: String,
        keyStorePassword: () -> CharArray,
        privateKeyPassword: () -> CharArray
    ) : this() {
        this.keyStore = keyStore
        this.keyAlias = keyAlias
        this.keyStorePassword = keyStorePassword
        this.privateKeyPassword = privateKeyPassword
    }

    private var _keyStore: KeyStore? = null
    private var _keyAlias: String? = null
    private var _keyStorePassword: (() -> CharArray)? = null
    private var _privateKeyPassword: (() -> CharArray)? = null

    override var keyStore: KeyStore
        get() = requireNotNull(_keyStore) { "keyStore is not set" }
        set(value) {
            _keyStore = value
        }
    override var keyAlias: String
        get() = requireNotNull(_keyAlias) { "keyAlias is not set" }
        set(value) {
            _keyAlias = value
        }
    override var keyStorePassword: () -> CharArray
        get() = requireNotNull(_keyStorePassword) { "keyStorePassword is not set" }
        set(value) {
            _keyStorePassword = value
        }
    override var privateKeyPassword: () -> CharArray
        get() = requireNotNull(_privateKeyPassword) { "privateKeyPassword is not set" }
        set(value) {
            _privateKeyPassword = value
        }
    override var keyStorePath: File? = null
    override var trustStore: KeyStore? = null
    override var trustStorePath: File? = null
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
        override var keyStoreProvider: KeyStoreProvider? = null
        override var keyAlias: String? = null

        public fun keyStore(keyStore: KeyStore) {
            keyStoreProvider = KeyStoreProvider.Instance(keyStore)
        }

        public fun keyStore(path: File, type: String = "JKS", passwordProvider: (() -> CharArray)? = null) {
            keyStoreProvider = KeyStoreProvider.File(path, type, passwordProvider)
        }

        public actual fun pkcs12Certificate(
            certificatePath: String,
            certificatePasswordProvider: (() -> CharArray)?
        ) {
            pkcs12Certificate(File(certificatePath), certificatePasswordProvider)
        }

        public fun pkcs12Certificate(
            certificatePath: File,
            certificatePasswordProvider: (() -> CharArray)?
        ) {
            keyStoreProvider = KeyStoreProvider.File(
                path = certificatePath,
                type = "PKCS12",
                passwordProvider = certificatePasswordProvider
            )
        }
    }

    public actual class VerificationConfigBuilder : EngineSSLConnectorConfig.VerificationConfig {
        override var trustStoreProvider: KeyStoreProvider? = null

        public fun trustStore(keyStore: KeyStore) {
            trustStoreProvider = KeyStoreProvider.Instance(keyStore)
        }

        public fun trustStore(path: File, type: String = "JKS", passwordProvider: (() -> CharArray)? = null) {
            trustStoreProvider = KeyStoreProvider.File(path, type, passwordProvider)
        }

        public actual fun pkcs12Certificate(
            certificatePath: String,
            certificatePasswordProvider: (() -> CharArray)?
        ) {
            pkcs12Certificate(File(certificatePath), certificatePasswordProvider)
        }

        public fun pkcs12Certificate(
            certificatePath: File,
            certificatePasswordProvider: (() -> CharArray)?
        ) {
            trustStoreProvider = KeyStoreProvider.File(
                path = certificatePath,
                type = "PKCS12",
                passwordProvider = certificatePasswordProvider
            )
        }
    }
}

/**
 * Represents an SSL connector configuration.
 */
public actual interface EngineSSLConnectorConfig : EngineConnectorConfig {
    /**
     * KeyStore where a certificate is stored
     */
    public val keyStore: KeyStore

    /**
     * File where the keystore is located
     */
    public val keyStorePath: File?

    /**
     * TLS key alias
     */
    public val keyAlias: String

    /**
     * Keystore password provider
     */
    public val keyStorePassword: () -> CharArray

    /**
     * Private key password provider
     */
    public val privateKeyPassword: () -> CharArray

    /**
     * Store of trusted certificates for verifying the remote endpoint's certificate.
     *
     * The engine tries to use [trustStore] first and uses [trustStorePath] as a fallback.
     *
     * If [trustStore] and [trustStorePath] are both null, the endpoint's certificate will not be verified.
     */
    public val trustStore: KeyStore?

    /**
     * File with trusted certificates (JKS) for verifying the remote endpoint's certificate.
     *
     * The engine tries to use [trustStore] first and uses [trustStorePath] as a fallback.
     *
     * If [trustStore] and [trustStorePath] are both null, the endpoint's certificate will not be verified.
     */
    public val trustStorePath: File?

    public actual val authentication: AuthenticationConfig?
    public actual val verification: VerificationConfig?

    public actual interface AuthenticationConfig {
        /**
         * Private key password provider
         */
        public actual val privateKeyPassword: () -> CharArray

        public val keyAlias: String?

        public val keyStoreProvider: KeyStoreProvider?
    }

    public actual interface VerificationConfig {
        public val trustStoreProvider: KeyStoreProvider?
    }

}

public sealed interface KeyStoreProvider {
    public data class Instance(
        public val keyStore: KeyStore,
    ) : KeyStoreProvider

    public data class File(
        public val path: java.io.File,
        public val type: String,
        public val passwordProvider: (() -> CharArray)?,
    ) : KeyStoreProvider
}

public fun KeyStoreProvider.resolveKeyStore(): KeyStore = when (this) {
    is KeyStoreProvider.File -> {
        KeyStore.getInstance(type).apply {
            val password = passwordProvider?.invoke()
            try {
                load(path.inputStream(), password)
            } finally {
                password?.fill('\u0000')
            }
        }
    }
    is KeyStoreProvider.Instance -> keyStore
}

// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.internal.openssl.*
import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import platform.posix.*

public actual class TLSConfig(
    public actual val isClient: Boolean,
    public actual val serverName: String?,
    public actual val authentication: TLSAuthenticationConfig?
) : Closeable {
    private var privateKey: CPointer<EVP_PKEY>? = null
    private var x509Certificate: CPointer<X509>? = null
    private val _sslContext = lazy {
        val context = SSL_CTX_new(TLS_method())!!

        //TODO: set peer
        SSL_CTX_set_verify(context, SSL_VERIFY_NONE, null)

        if (authentication?.certificate != null) {
            val privateKeyPassword = authentication.privateKeyPassword.invoke()
            val passwordRef = StableRef.create(privateKeyPassword.concatToString())
            privateKeyPassword.fill('\u0000')
            try {
                SSL_CTX_set_default_passwd_cb_userdata(context, passwordRef.asCPointer())
                SSL_CTX_set_default_passwd_cb(context, passwordCheckFunction)

                authentication.certificate.parse { privateKey, x509Certificate ->
                    check(SSL_CTX_use_certificate(context, x509Certificate) == 1) { "Failed to set X509 certificate" }
                    check(SSL_CTX_use_PrivateKey(context, privateKey) == 1) { "Failed to set EVP private key" }

                    this.privateKey = privateKey
                    this.x509Certificate = x509Certificate
                }
            } finally {
                passwordRef.dispose()
            }
        }
        context
    }

    public val sslContext: CPointer<SSL_CTX> by _sslContext

    override fun close() {
        if (_sslContext.isInitialized()) {
            EVP_PKEY_free(privateKey)
            X509_free(x509Certificate)
            SSL_CTX_free(_sslContext.value)
        }
    }
}

public actual class TLSAuthenticationConfig(
    public val certificate: PKCS12Certificate?,
    public val privateKeyPassword: () -> CharArray
)

private val passwordCheckFunction: CPointer<pem_password_cb> = staticCFunction { buf, size, rwFlag, u ->
    val stringPassword = u?.asStableRef<String>()?.get() ?: return@staticCFunction 0
    stringPassword.encodeToByteArray().usePinned {
        memcpy(buf, it.addressOf(0), stringPassword.length.convert())
    }
    stringPassword.length
}

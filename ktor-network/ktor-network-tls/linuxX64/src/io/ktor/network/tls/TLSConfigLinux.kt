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
    public actual val authentication: TLSAuthenticationConfig?,
    public actual val verification: TLSVerificationConfig?,
) : Closeable {
    private val _sslContext = lazy {
        val context = SSL_CTX_new(TLS_method())!!

        authentication?.configure(context)
        verification?.configure(context)

        context
    }

    public val sslContext: CPointer<SSL_CTX> by _sslContext

    override fun close() {
        authentication?.close()
        verification?.close()
        if (_sslContext.isInitialized()) {
            SSL_CTX_free(_sslContext.value)
        }
    }
}

public actual class TLSAuthenticationConfig(
    public val certificate: PKCS12Certificate?,
    public val privateKeyPassword: () -> CharArray
) : Closeable {
    override fun close() {
        certificate?.close()
    }

    internal fun configure(context: CPointer<SSL_CTX>) {
        if (certificate == null) return

        val charPassword = privateKeyPassword.invoke()
        val password = charPassword.concatToString()
        charPassword.fill('\u0000')

        SSL_CTX_set_default_passwd_cb_userdata(context, password.refTo(0))
        SSL_CTX_set_default_passwd_cb(context, passwordCheckFunction)
        check(
            SSL_CTX_use_certificate(context, certificate.x509Certificate) == 1
        ) { "Failed to set X509 certificate for authentication" }
        check(
            SSL_CTX_use_PrivateKey(context, certificate.privateKey) == 1
        ) { "Failed to set EVP private key for authentication" }
    }
}

public actual class TLSVerificationConfig(
    public val certificate: PKCS12Certificate?,
) : Closeable {
    override fun close() {
        certificate?.close()
    }

    internal fun configure(context: CPointer<SSL_CTX>) {
        if (certificate == null) {
            SSL_CTX_set_verify(context, SSL_VERIFY_NONE, null)
            return
        }

        SSL_CTX_set_verify(context, SSL_VERIFY_PEER or SSL_VERIFY_FAIL_IF_NO_PEER_CERT, verificationFunction)

        //store will be automatically freed when context is freed - TODO: how to check it?
        val store = X509_STORE_new()!!

        check(
            X509_STORE_add_cert(store, certificate.x509Certificate) == 1
        ) { "Failed to set X509 certificate for verification" }

        check(
            SSL_CTX_ctrl(context, SSL_CTRL_SET_VERIFY_CERT_STORE, 1, store) == 1L
        ) { "Failed to set X509 certificate store for verification" }
    }
}

private val passwordCheckFunction: CPointer<pem_password_cb> = staticCFunction { buf, size, rwFlag, u ->
    val stringPassword = u?.asStableRef<String>()?.get() ?: return@staticCFunction 0
    memcpy(buf, stringPassword.encodeToByteArray().refTo(0), stringPassword.length.convert())
    stringPassword.length
}

private val verificationFunction: SSL_verify_cb = staticCFunction { preverifyOk, store ->
    //TODO: somehow pass this error outside
    println(X509_verify_cert_error_string(X509_STORE_CTX_get_error(store).toLong())?.toKString())
    preverifyOk
}

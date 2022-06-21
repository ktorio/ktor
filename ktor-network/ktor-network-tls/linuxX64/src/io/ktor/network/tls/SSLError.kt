/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.internal.openssl.*

internal enum class SSLError(private val code: Int) {
    WantRead(SSL_ERROR_WANT_READ),
    WantWrite(SSL_ERROR_WANT_WRITE),
    WANT_ASYNC(SSL_ERROR_WANT_ASYNC),
    WANT_ASYNC_JOB(SSL_ERROR_WANT_ASYNC_JOB),
    WANT_CLIENT_HELLO_CB(SSL_ERROR_WANT_CLIENT_HELLO_CB),
    WANT_X509_LOOKUP(SSL_ERROR_WANT_X509_LOOKUP),
    Closed(SSL_ERROR_ZERO_RETURN);

    companion object {
        private val values: Array<SSLError?>

        init {
            val enums = values()
            val maxCode = enums.maxOf { it.code }
            values = arrayOfNulls<SSLError?>(maxCode + 1)
            enums.forEach { values[it.code] = it }
        }

        operator fun invoke(code: Int): SSLError = values[code] ?: error("Unknown code: $code")
    }
}

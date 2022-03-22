/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.locks.*
import kotlinx.cinterop.*
import openssl.*

@OptIn(InternalAPI::class)
internal class Ssl(private val ssl: CPointer<SSL>) : SslEngine() {
    private val lock = SynchronizedObject()
    fun attach(reader: ByteReadChannel, writer: ByteWriteChannel): SslPipe {
        val output = BIO_new(BIO_s_mem())!!
        val input = BIO_new(BIO_s_mem())!!
        SSL_set_bio(s = ssl, rbio = output, wbio = input)
        return SslPipe(reader, writer, input, output)
    }

    inline fun <T> write(
        source: CPointer<ByteVar>, sourceOffset: Int, sourceLength: Int,
        onSuccess: (consumed: Int) -> T,
        onError: (error: SslError) -> T
    ): T = ssl.operation({
        //println("ENGINE.WRITE.SUCCESS: $it")
        onSuccess(it)
    }, {
        //println("ENGINE.WRITE.ERROR: $it")
        onError(it)
    }) {
        SSL_write(this, source + sourceOffset, sourceLength - sourceOffset)
    }

    internal inline fun <T> read(
        destination: CPointer<ByteVar>, destinationOffset: Int, destinationLength: Int,
        onSuccess: (produced: Int) -> T,
        onError: (error: SslError) -> T
    ): T = ssl.operation({
        //println("ENGINE.READ.SUCCESS: $it")
        onSuccess(it)
    }, {
        //println("ENGINE.READ.ERROR: $it")
        onError(it)
    }) {
        SSL_read(this, destination + destinationOffset, destinationLength - destinationOffset)
    }

    private inline fun <T> CPointer<SSL>.operation(
        onSuccess: (result: Int) -> T,
        onError: (error: SslError) -> T,
        block: CPointer<SSL>.() -> Int
    ): T = lock.withLock {
        ERR_clear_error()
        val result = block(this)
        if (result > 0) {
            onSuccess(result)
        } else {
            onError(
                when (val code = SSL_get_error(this, result)) {
                    SSL_ERROR_NONE -> TODO("never")
                    SSL_ERROR_SSL -> {
                        val message = getSslErrorMessage()
                        error("SSL error: $message")
                    }
                    SSL_ERROR_SYSCALL -> {
                        val message = getSslErrorMessage()
                        error("SSL SYSCALL error: $message")
                    }
                    else -> SslError(code)
                }
            )
        }
    }

    private fun getSslErrorMessage(): String? = when (val errorCode = ERR_get_error()) {
        0UL -> null
        else -> memScoped {
            val pointer = allocArray<ByteVar>(256)
            ERR_error_string(errorCode, pointer)
            pointer.toKString()
        }
    }
}

internal enum class SslError(private val code: Int) {
    WantRead(SSL_ERROR_WANT_READ),
    WantWrite(SSL_ERROR_WANT_WRITE),
    WANT_ASYNC(SSL_ERROR_WANT_ASYNC),
    WANT_ASYNC_JOB(SSL_ERROR_WANT_ASYNC_JOB),
    WANT_CLIENT_HELLO_CB(SSL_ERROR_WANT_CLIENT_HELLO_CB),
    WANT_X509_LOOKUP(SSL_ERROR_WANT_X509_LOOKUP),
    Closed(SSL_ERROR_ZERO_RETURN);

    //    WANT_ACCEPT(SSL_ERROR_WANT_ACCEPT),
    //    WANT_CONNECT(SSL_ERROR_WANT_CONNECT),
    companion object {
        private val values: Array<SslError?>

        init {
            val enums = values()
            val maxCode = enums.maxOf { it.code }
            values = arrayOfNulls<SslError?>(maxCode + 1)
            enums.forEach { values[it.code] = it }
        }

        operator fun invoke(code: Int): SslError = values[code] ?: error("Unknown code: $code")
    }
}

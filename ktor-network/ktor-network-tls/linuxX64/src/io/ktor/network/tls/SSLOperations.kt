/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.network.tls.internal.openssl.*
import io.ktor.utils.io.errors.*
import kotlinx.atomicfu.locks.*
import kotlinx.cinterop.*
import platform.posix.*

internal class SSLOperations(
    private val ssl: CPointer<SSL>,
    val debugString: String
) {
    private val operationsLock = SynchronizedObject()

    inline fun <T> write(
        source: CPointer<ByteVar>, sourceOffset: Int, sourceLength: Int,
        onSuccess: (consumed: Int) -> T,
        onError: (error: SSLError) -> T,
        onClose: () -> T
    ): T {
        //println("[$debugString] ENGINE.WRITE.START")
        return ssl.operation({
            //println("[$debugString] ENGINE.WRITE.SUCCESS: $it")
            onSuccess(it)
        }, {
            //println("[$debugString] ENGINE.WRITE.ERROR: $it")
            onError(it)
        }, {
            //println("[$debugString] ENGINE.WRITE.CLOSE")
            onClose()
        }) {
            SSL_write(this, source + sourceOffset, sourceLength - sourceOffset)
        }
    }

    internal inline fun <T> read(
        destination: CPointer<ByteVar>, destinationOffset: Int, destinationLength: Int,
        onSuccess: (produced: Int) -> T,
        onError: (error: SSLError) -> T,
        onClose: () -> T
    ): T {
        //println("[$debugString] ENGINE.READ.START")
        return ssl.operation({
            //println("[$debugString] ENGINE.READ.SUCCESS: $it")
            onSuccess(it)
        }, {
            //println("[$debugString] ENGINE.READ.ERROR: $it")
            onError(it)
        }, {
            //println("[$debugString] ENGINE.READ.CLOSE")
            onClose()
        }) {
            SSL_read(this, destination + destinationOffset, destinationLength - destinationOffset)
        }
    }

    private inline fun <T> CPointer<SSL>.operation(
        onSuccess: (result: Int) -> T,
        onError: (error: SSLError) -> T,
        onClose: () -> T,
        block: CPointer<SSL>.() -> Int
    ): T = operationsLock.withLock {
        ERR_clear_error()
        val result = block(this)

        if (result > 0) return@withLock onSuccess(result)
        if (result == 0) return@withLock onClose()

        onError(
            when (val code = SSL_get_error(this, result)) {
                SSL_ERROR_NONE -> TODO("never")
                SSL_ERROR_SSL -> {
                    val message = getSslErrorMessage()
                    error("SSL error: $message")
                }
                SSL_ERROR_SYSCALL -> {
                    if (errno != 0) throw PosixException.forErrno()
                    val message = getSslErrorMessage()
                    error("SSL SYSCALL error: $message")
                }
                else -> SSLError(code)
            }
        )
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

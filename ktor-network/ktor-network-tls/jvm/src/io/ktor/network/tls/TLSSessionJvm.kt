/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.network.tls

import io.ktor.network.sockets.*
import io.ktor.network.tls.NetworkRole.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*

internal actual suspend fun openTLSSession(
    socket: Socket,
    input: ByteReadChannel,
    output: ByteWriteChannel,
    config: TLSConfig,
    context: CoroutineContext
): Socket {
    TLSSocketAdaptor(input, output, config, context).let { connector ->
        try {
            connector.negotiate(config.algorithm)
            return connector.wrap(socket)
        } catch (cause: ClosedSendChannelException) {
            throw TLSClosedChannelException(cause)
        } catch (cause: Throwable) {
            connector.close()
            throw when (cause) {
                is TLSException -> cause
                is TimeoutCancellationException -> TLSHandshakeTimeoutException(cause)
                else -> TLSNegotiationException(cause = cause)
            }
        }
    }
}

private val TLSConfig.algorithm get() =
    when (role) {
        SERVER -> TLSServerHandshake
        CLIENT -> TLSClientHandshake
    }

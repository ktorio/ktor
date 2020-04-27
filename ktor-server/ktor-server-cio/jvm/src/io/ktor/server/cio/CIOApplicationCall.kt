/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.http.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import io.ktor.utils.io.*
import java.net.*
import kotlin.coroutines.*

internal class CIOApplicationCall(
    application: Application,
    _request: Request,
    input: ByteReadChannel,
    output: ByteWriteChannel,
    engineDispatcher: CoroutineContext,
    appDispatcher: CoroutineContext,
    upgraded: CompletableDeferred<Boolean>?,
    remoteAddress: SocketAddress?,
    localAddress: SocketAddress?
) : BaseApplicationCall(application) {

    override val request = CIOApplicationRequest(
        this,
        remoteAddress as? InetSocketAddress,
        localAddress as? InetSocketAddress,
        input,
        _request
    )

    override val response = CIOApplicationResponse(this, output, input, engineDispatcher, appDispatcher, upgraded)

    internal fun release() {
        request.release()
    }

    init {
        putResponseAttribute()
    }
}

package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.http.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*

class CIOApplicationCall(application: Application,
                         _request: Request,
                         input: ByteReadChannel,
                         output: ByteWriteChannel,
                         engineDispatcher: CoroutineContext,
                         appDispatcher: CoroutineContext,
                         upgraded: CompletableDeferred<Boolean>?) : BaseApplicationCall(application) {

    override val request = CIOApplicationRequest(this, input, _request)
    override val response = CIOApplicationResponse(this, output, input, engineDispatcher, appDispatcher, upgraded)

    internal fun release() {
        request.release()
    }
}
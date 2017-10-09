package io.ktor.cio.http

import io.ktor.application.*
import io.ktor.host.*
import io.ktor.http.cio.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*

class CIOApplicationCall(application: Application,
                         private val _request: Request,
                         private val input: ByteReadChannel,
                         private val output: ByteWriteChannel,
                         private val hostDispatcher: CoroutineContext,
                         private val appDispatcher: CoroutineContext) : BaseApplicationCall(application) {

    override val request = CIOApplicationRequest(this, input, _request)
    override val response = CIOApplicationResponse(this, output, input, hostDispatcher, appDispatcher)

}
package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import kotlin.coroutines.experimental.*

class CIOApplicationCall(application: Application,
                         private val _request: Request,
                         private val input: ByteReadChannel,
                         private val output: ByteWriteChannel,
                         private val multipart: ReceiveChannel<MultipartEvent>,
                         private val hostDispatcher: CoroutineContext,
                         private val appDispatcher: CoroutineContext) : BaseApplicationCall(application) {

    override val request = CIOApplicationRequest(this, input, multipart, _request)
    override val response = CIOApplicationResponse(this, output, input, hostDispatcher, appDispatcher)

}
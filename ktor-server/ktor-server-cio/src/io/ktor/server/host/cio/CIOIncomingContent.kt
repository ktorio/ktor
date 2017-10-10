package io.ktor.server.host.cio

import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.cio.*
import io.ktor.request.*
import kotlinx.coroutines.experimental.io.*

class CIOIncomingContent(private val channel: ByteReadChannel,
                         private val headers: HttpHeaders,
                         override val request: CIOApplicationRequest) : IncomingContent {

    private fun byteChannel(): ByteReadChannel = channel

    override fun readChannel(): ReadChannel = CIOReadChannelAdapter(byteChannel())

    override fun multiPartData(): MultiPartData {
        return CIOMultipartData(byteChannel(), headers)
    }
}
package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.io.*
import kotlinx.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.request.*

class CIOIncomingContent(private val channel: ByteReadChannel,
                         private val headers: HttpHeaders,
                         override val request: CIOApplicationRequest) : IncomingContent {

    private fun byteChannel(): ByteReadChannel = channel

    override fun readChannel(): ReadChannel = CIOReadChannelAdapter(byteChannel())

    override fun multiPartData(): MultiPartData {
        return CIOMultipartData(byteChannel(), headers)
    }
}
package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.http.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.request.*
import java.nio.ByteBuffer

class CIOIncomingContent(private val channel: ByteReadChannel, private val multipart: ReceiveChannel<MultipartEvent>, override val request: CIOApplicationRequest) : IncomingContent {
    override fun readChannel(): ReadChannel {
        return object : ReadChannel {
            suspend override fun read(dst: ByteBuffer) = channel.readAvailable(dst)

            override fun close() {
            }
        }
    }

    override fun multiPartData(): MultiPartData {
        return CIOMultipartData(multipart)
    }
}
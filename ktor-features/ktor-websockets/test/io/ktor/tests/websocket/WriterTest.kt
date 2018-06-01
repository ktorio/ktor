package io.ktor.tests.websocket

import io.ktor.cio.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.coroutines.experimental.*
import kotlin.test.*

class WriterTest {
    @Test
    fun testWriteBigThenClose() = runBlocking {
        val out = ByteChannel()
        val writer = @Suppress("DEPRECATION") WebSocketWriter(out, Job(), coroutineContext)

        val body = ByteBuffer.allocate(65535)
        while (body.hasRemaining()) {
            body.put(0x77)
        }
        body.flip()

        writer.send(Frame.Binary(true, body))
        writer.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "")))

        val bytesWritten = out.toByteArray().takeLast(4).joinToString {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

        assertEquals(true, writer.outgoing.isClosedForSend)
        assertEquals("88, 02, 03, e8", bytesWritten)
    }

    @Test
    fun testWriteDataAfterClose() = runBlocking {
        val out = ByteChannel()
        val writer = @Suppress("DEPRECATION") (WebSocketWriter(out, Job(), coroutineContext))

        writer.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "")))
        writer.send(Frame.Text("Yo"))

        val bytesWritten = out.toByteArray().takeLast(4).joinToString {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }

        (writer.outgoing as Job).join()

        assertEquals(true, writer.outgoing.isClosedForSend)
        assertEquals("88, 02, 03, e8", bytesWritten)
    }
}

package io.ktor.tests.websocket

import io.ktor.cio.*
import io.ktor.websocket.*
import kotlinx.coroutines.experimental.*
import org.junit.Test
import java.nio.*
import kotlin.test.*

class WriterTest {
    @Test
    fun testWriteBigThenClose() = runBlocking {
        val buffer = ByteBufferWriteChannel()
        val job = Job()
        val out = object : WriteChannel {
            suspend override fun write(src: ByteBuffer) {
                yield() // yield is cancellable
                buffer.write(src)
            }

            suspend override fun flush() {
                yield()
                buffer.flush()
            }

            override fun close() {
                buffer.close()
            }
        }
        val writer = WebSocketWriter(out, job, coroutineContext, NoPool)

        val body = ByteBuffer.allocate(65535)
        while (body.hasRemaining()) {
            body.put(0x77)
        }
        body.flip()

        writer.send(Frame.Binary(true, body))
        writer.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "")))

        (writer.outgoing as Job).join()

        assertEquals(true, writer.outgoing.isClosedForSend)

        val bytesWritten = buffer.toByteArray().takeLast(4).joinToString { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

        assertEquals("88, 02, 03, e8", bytesWritten)
    }

    @Test
    fun testWriteDataAfterClose() = runBlocking {
        val out = ByteBufferWriteChannel()
        val job = Job()
        val writer = WebSocketWriter(out, job, coroutineContext, NoPool)

        writer.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "")))
        writer.send(Frame.Text("Yo"))

        (writer.outgoing as Job).join()

        assertEquals(true, writer.outgoing.isClosedForSend)

        val bytesWritten = out.toByteArray().takeLast(4).joinToString { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

        assertEquals("88, 02, 03, e8", bytesWritten)
    }
}

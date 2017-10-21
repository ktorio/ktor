package io.ktor.tests.server.nio

import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class ReadChannelFromOutputStreamTest {
    @Test fun streamByte() = runBlocking {
        val osc = ReadChannelFromOutputStream()
        osc.write(42)
        val dst = ByteBuffer.allocate(1)
        val count = osc.read(dst)
        assertEquals(1, count)
        dst.flip()
        assertEquals(42, dst.get())
    }

    @Test fun streamByteReadFirst() = runBlocking {
        val osc = ReadChannelFromOutputStream()
        val dst = ByteBuffer.allocate(1)
        val count = async(coroutineContext) {
            osc.read(dst)
        }
        osc.write(42)
        assertEquals(1, count.await())
        dst.flip()
        assertEquals(42, dst.get())
    }

    @Test fun streamString() = runBlocking {
        val osc = ReadChannelFromOutputStream()
        val sendText = "Hello, Coroutine!"
        osc.writer().apply {
            write(sendText)
            flush()
        }
        osc.close()
        val text = osc.toInputStream().reader().readText()
        assertEquals(sendText, text)
    }
}

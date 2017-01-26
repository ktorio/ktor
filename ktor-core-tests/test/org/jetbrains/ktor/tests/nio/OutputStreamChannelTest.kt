package org.jetbrains.ktor.tests.nio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.cio.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class OutputStreamChannelTest {
    @Test fun streamByte() = runBlocking {
        val osc = OutputStreamChannel()
        osc.write(42)
        val dst = ByteBuffer.allocate(1)
        val count = osc.read(dst)
        assertEquals(1, count)
        dst.flip()
        assertEquals(42, dst.get())
    }

    @Test fun streamByteReadFirst() = runBlocking {
        val osc = OutputStreamChannel()
        val dst = ByteBuffer.allocate(1)
        val count = defer(context) {
            osc.read(dst)
        }
        osc.write(42)
        assertEquals(1, count.await())
        dst.flip()
        assertEquals(42, dst.get())
    }

    @Test fun streamString() = runBlocking {
        val osc = OutputStreamChannel()
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

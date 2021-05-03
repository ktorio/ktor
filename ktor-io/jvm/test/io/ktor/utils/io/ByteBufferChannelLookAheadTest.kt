package kotlinx.coroutines.experimental.io

import io.ktor.utils.io.*
import io.ktor.utils.io.core.writeInt
import kotlin.test.*

class ByteBufferChannelLookAheadTest : ByteChannelTestBase() {
    @Test
    fun testDoNothing() = runTest {
        ch.lookAheadSuspend {
        }
    }

    @Test
    fun testDoNothingWhileWriting() = runTest {
        ch.writeSuspendSession {
            ch.lookAheadSuspend {
            }
        }
    }

    @Test
    fun testDoNothingWhileWriting2() = runTest {
        ch.lookAheadSuspend {
            ch.writeSuspendSession {
            }
        }
    }

    @Test
    fun testLookAheadSuspendOnFailedChannel() = runTest {
        ch.close(RuntimeException("Closed"))
        ch.lookAheadSuspend {
            assertFailsWith<RuntimeException> { request(1, 1) }
            assertFailsWith<RuntimeException> { awaitAtLeast(1) }
            assertFailsWith<RuntimeException> { consumed(1) }
        }
    }

    @Test
    fun testLookAheadOnFailedChannel() = runTest {
        ch.close(RuntimeException("Closed"))
        ch.lookAhead {
            assertFailsWith<RuntimeException> { request(1, 1) }
            assertFailsWith<RuntimeException> { consumed(1) }
        }
    }

    @Test
    fun testReadDuringWriting() = runTest {
        ch.writeSuspendSession {
            ch.lookAheadSuspend {
                this@writeSuspendSession.request(1)!!.writeInt(777)
                written(4)
                flush()

                val bb = request(0, 1)
                assertNotNull(bb)
                assertEquals(777, bb.getInt())
                consumed(4)
            }
        }
    }
}

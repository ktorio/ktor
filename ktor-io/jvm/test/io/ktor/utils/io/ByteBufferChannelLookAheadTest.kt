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

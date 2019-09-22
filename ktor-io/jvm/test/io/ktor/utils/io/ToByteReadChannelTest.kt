package kotlinx.coroutines.experimental.io

import kotlinx.coroutines.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import java.io.*
import java.nio.*
import java.util.*
import kotlin.test.*

class ToByteReadChannelTest {
    @Test
    fun testEmpty() = runBlocking<Unit> {
        val channel = ByteArrayInputStream(ByteArray(0)).toByteReadChannel()
        channel.readRemaining().use { pkt ->
            assertTrue { pkt.isEmpty }
        }
    }

    @Test
    fun testSeveralBytes() = runBlocking<Unit> {
        val content = byteArrayOf(1, 2, 3, 4)
        val channel = ByteArrayInputStream(content).toByteReadChannel()
        channel.readRemaining().use { pkt ->
            val bytes = pkt.readBytes()
            assertTrue { bytes.contentEquals(content) }
        }
    }

    @Test
    fun testBigStream() = runBlocking<Unit> {
        val content = ByteArray(65536 * 8)
        Random().nextBytes(content)

        val channel = ByteArrayInputStream(content).toByteReadChannel()
        channel.readRemaining().use { pkt ->
            val bytes = pkt.readBytes()
            assertTrue { bytes.contentEquals(content) }
        }
    }

    @Test
    fun testEmptyBB() = runBlocking<Unit> {
        val channel = ByteArrayInputStream(ByteArray(0)).toByteReadChannel(pool = pool)
        channel.readRemaining().use { pkt ->
            assertTrue { pkt.isEmpty }
        }
    }

    @Test
    fun testSeveralBytesBB() = runBlocking<Unit> {
        val content = byteArrayOf(1, 2, 3, 4)
        val channel = ByteArrayInputStream(content).toByteReadChannel(pool = pool)
        channel.readRemaining().use { pkt ->
            val bytes = pkt.readBytes()
            assertTrue { bytes.contentEquals(content) }
        }
    }

    @Test
    fun testBigStreamBB() = runBlocking<Unit> {
        val content = ByteArray(65536 * 8)
        Random().nextBytes(content)

        val channel = ByteArrayInputStream(content).toByteReadChannel(pool = pool)
        channel.readRemaining().use { pkt ->
            val bytes = pkt.readBytes()
            assertTrue { bytes.contentEquals(content) }
        }
    }

    companion object {
        private val pool = object : DefaultPool<ByteBuffer>(10) {
            override fun produceInstance(): ByteBuffer {
                return ByteBuffer.allocate(4096)
            }
        }
    }
}

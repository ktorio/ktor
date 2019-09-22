package io.ktor.utils.io

import kotlin.test.*

abstract class ByteChannelTestBase(autoFlush: Boolean = false) {
    protected val coroutines = DummyCoroutines()
    protected val ch: ByteChannel by lazy { ByteChannel(autoFlush) }
    protected val Size = 4096 - 8
    private var current = 0

    @AfterTest
    fun finish() {
        ch.close(CancellationException("Test finished"))
    }

    protected open fun ByteChannel(autoFlush: Boolean): ByteChannel {
        return io.ktor.utils.io.ByteChannel(autoFlush)
    }

    protected fun runTest(block: suspend () -> Unit) {
        coroutines.schedule(block)
        coroutines.run()
    }

    protected fun launch(block: suspend () -> Unit) {
        coroutines.schedule(block)
    }

    protected suspend fun yield() {
        return coroutines.yield()
    }

    protected fun expect(n: Int) {
        val next = current + 1
        assertNotEquals(0, next, "Already finished")
        assertEquals(n, next, "Invalid test state")
        current = next
    }

    protected fun finish(n: Int) {
        expect(n)
        current = -1
    }
}

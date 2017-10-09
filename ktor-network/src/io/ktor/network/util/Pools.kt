package io.ktor.network.util

import io.ktor.network.sockets.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

val ioThreadGroup = ThreadGroup("io-pool-group")
val selectorsGroup = ThreadGroup("selector-pool-group")

private val cpuCount = Runtime.getRuntime().availableProcessors()

val ioCoroutineDispatcher: CoroutineDispatcher = IOCoroutineDispatcher(maxOf(2, (cpuCount * 2 / 3)))

private val selectorsPool = ThreadPoolExecutor(
        1, Int.MAX_VALUE,
        10L, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(100),
        GroupThreadFactory(selectorsGroup, true))

internal val selectorsCoroutineDispatcher = selectorsPool.asCoroutineDispatcher()

private class GroupThreadFactory(val group: ThreadGroup, val isDaemon: Boolean) : ThreadFactory {
    private val counter = AtomicInteger()

    override fun newThread(r: Runnable?): Thread {
        return Thread(group, r, group.name + counter.incrementAndGet()).apply {
            isDaemon = this@GroupThreadFactory.isDaemon
        }
    }
}

internal val DefaultDatagramByteBufferPool: ObjectPool<ByteBuffer> = DirectByteBufferPool(MAX_DATAGRAM_SIZE, 2048)

internal class DirectByteBufferPool(val bufferSize: Int, size: Int) : DefaultPool<ByteBuffer>(size) {
    override fun produceInstance(): ByteBuffer = java.nio.ByteBuffer.allocateDirect(bufferSize)

    override fun clearInstance(instance: ByteBuffer): ByteBuffer {
        instance.clear()
        return instance
    }

    override fun validateInstance(instance: ByteBuffer) {
        require(instance.isDirect)
        require(instance.capacity() == bufferSize)
    }
}

internal class IOCoroutineDispatcher(val nThreads: Int) : CoroutineDispatcher() {
    private val dispatcherThreadGroup = ThreadGroup(ioThreadGroup, "io-pool-group-sub")
    private val tasks = Channel<Runnable>(Channel.UNLIMITED)

    init {
        require(nThreads > 0) { "nThreads should be positive but $nThreads specified"}
    }

    private val threads = (1..nThreads).map {
        IOThread().apply { start() }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (!tasks.offer(block)) {
            CommonPool.dispatch(context, block)
        }
    }

    private inner class IOThread : Thread(dispatcherThreadGroup, "io-thread") {
        init {
            isDaemon = true
        }

        override fun run() {
            runBlocking {
                while (true) {
                    val task = tasks.receiveOrNull() ?: break
                    run(task)
                }
            }
        }

        private fun run(task: Runnable) {
            try {
                task.run()
            } catch (t: Throwable) {
            }
        }
    }
}
package io.ktor.network.util

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import java.io.*
import kotlin.coroutines.experimental.*

internal class IOCoroutineDispatcher(private val nThreads: Int) : CoroutineDispatcher(), Closeable {
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

    override fun close() {
        tasks.close()
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
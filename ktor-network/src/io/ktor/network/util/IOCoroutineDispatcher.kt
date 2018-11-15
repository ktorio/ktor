package io.ktor.network.util

import io.ktor.util.*
import io.ktor.util.internal.*
import kotlinx.coroutines.*
import kotlinx.io.core.Closeable
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Default ktor fixed size dispatcher for doing non-blocking I/O operations and selection
 */
@Deprecated("This is going to be deprecated. Use kotlinx.coroutines dispatchers")
@KtorExperimentalAPI
class IOCoroutineDispatcher(private val nThreads: Int) : CoroutineDispatcher(), Closeable {
    @Suppress("DEPRECATION")
    private val dispatcherThreadGroup = ThreadGroup(ioThreadGroup, "io-pool-group-sub")
    private val tasks = LockFreeLinkedListHead()

    init {
        require(nThreads > 0) { "nThreads should be positive but $nThreads specified" }
    }

    private val threads = Array(nThreads) {
        IOThread(it + 1, tasks, dispatcherThreadGroup)
    }

    init {
        threads.forEach {
            it.start()
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val node = IODispatchedTask(block).also { tasks.addLast(it) }
        resumeAnyThread(node)
    }

    /**
     * Gracefully shutdown dispatcher.
     */
    override fun close() {
        if (tasks.prev is Poison) return
        tasks.addLastIfPrev(Poison()) { prev -> prev !is Poison }
        resumeAllThreads()
    }

    private fun resumeAnyThread(node: LockFreeLinkedListNode) {
        val threads = threads
        @Suppress("LoopToCallChain")
        for (i in 0 until nThreads) {
            if (threads[i].tryResume() || node.isRemoved) return
        }
    }

    private fun resumeAllThreads() {
        val threads = threads
        for (i in 0 until nThreads) {
            threads[i].tryResume()
        }
    }

    private class IOThread(
            private val number: Int,
            private val tasks: LockFreeLinkedListHead,
            dispatcherThreadGroup: ThreadGroup) : Thread(dispatcherThreadGroup, "io-thread-$number") {

        @kotlin.jvm.Volatile
        private var cont: Continuation<Unit>? = null

        init {
            isDaemon = true
        }

        override fun run() {
            runBlocking(CoroutineName("io-dispatcher-executor-$number")) {
                try {
                    while (true) {
                        val task = receiveOrNull() ?: break
                        try {
                            task.run()
                        } catch (t: Throwable) {
                            onException(ExecutionException("Task failed", t))
                        }
                    }
                } catch (t: Throwable) {
                    onException(ExecutionException("Thread pool worker failed", t))
                }
            }
        }

        fun tryResume(): Boolean {
            val cont = ThreadCont.getAndSet(this, null)
            if (cont != null) {
                cont.resume(Unit)
                return true
            }
            return false
        }

        private fun onException(t: Throwable) {
            Thread.currentThread().uncaughtExceptionHandler.uncaughtException(this, t)
        }

        @Suppress("NOTHING_TO_INLINE")
        private suspend inline fun receiveOrNull(): Runnable? {
            val r = tasks.removeFirstIfIsInstanceOf<Runnable>()
            if (r != null) return r
            return receiveOrNullSuspend()
        }

        @Suppress("NOTHING_TO_INLINE")
        private suspend inline fun receiveOrNullSuspend(): Runnable? {
            do {
                val t = tasks.removeFirstIfIsInstanceOf<Runnable>()
                if (t != null) return t
                if (tasks.next is Poison) return null
                waitForTasks()
            } while (true)
        }

        private val awaitSuspendBlock = { raw: Continuation<Unit>? ->
            val continuation = raw?.intercepted()
            // nullable param is to avoid null check
            // we know that it is always non-null
            // and it will never crash if it is actually null
            val threadCont = ThreadCont
            if (!threadCont.compareAndSet(this, null, continuation)) throw IllegalStateException("Failed to set continuation")
            if (tasks.next !== tasks && threadCont.compareAndSet(this, continuation, null)) Unit
            else COROUTINE_SUSPENDED
        }

        private suspend fun waitForTasks() {
            return suspendCoroutineUninterceptedOrReturn(awaitSuspendBlock)
        }

        companion object {
            @Suppress("UNCHECKED_CAST")
            @JvmStatic
            private val ThreadCont =
                    AtomicReferenceFieldUpdater.newUpdater<IOThread, Continuation<*>>(IOThread::class.java, Continuation::class.java, IOThread::cont.name)
                            as AtomicReferenceFieldUpdater<IOThread, Continuation<Unit>?>
        }
    }

    private class Poison : LockFreeLinkedListNode()
    private class IODispatchedTask(val r: Runnable) : LockFreeLinkedListNode(), Runnable by r
}

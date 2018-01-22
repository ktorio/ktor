package io.ktor.network.util

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.internal.*
import kotlin.coroutines.experimental.intrinsics.*
import java.io.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

class IOCoroutineDispatcher(private val nThreads: Int) : CoroutineDispatcher(), Closeable {
    private val dispatcherThreadGroup = ThreadGroup(ioThreadGroup, "io-pool-group-sub")
    private val tasks = LockFreeLinkedListHead()

    init {
        require(nThreads > 0) { "nThreads should be positive but $nThreads specified"}
    }

    private val threads = (1..nThreads).map {
        IOThread().apply { start() }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val node: LockFreeLinkedListNode = if (block is LockFreeLinkedListNode && block.isFresh) {
            tasks.addLast(block)
            block
        } else {
            IODispatchedTask(block).also { tasks.addLast(it) }
        }
        resumeAnyThread(node)
    }

    override fun close() {
        if (tasks.prev is Poison) return
        tasks.addLastIfPrev(Poison()) { prev -> prev !is Poison }
        resumeAllThreads()
    }

    private fun resumeAnyThread(node: LockFreeLinkedListNode) {
        val threads = threads
        @Suppress("LoopToCallChain")
        for (i in 0 until nThreads) {
            val cont = ThreadCont.getAndSet(threads[i], null)
            if (cont != null) {
                cont.resume(Unit)
                return
            } else if (node.isRemoved) return
        }
    }

    private fun resumeAllThreads() {
        val threads = threads
        for (i in 0 until nThreads) {
            ThreadCont.getAndSet(threads[i], null)?.resume(Unit)
        }
    }

    internal inner class IOThread : Thread(dispatcherThreadGroup, "io-thread") {
        @Volatile
        @JvmField
        var cont : Continuation<Unit>? = null

        init {
            isDaemon = true
        }

        override fun run() {
            runBlocking(CoroutineName("io-dispatcher-executor")) {
                try {
                    while (true) {
                        val task = receiveOrNull() ?: break
                        run(task)
                    }
                } catch (t: Throwable) {
                    println("thread died: $t")
                }
            }
        }

        @Suppress("NOTHING_TO_INLINE")
        private suspend inline fun receiveOrNull(): Runnable? {
            val r = tasks.removeFirstIfIsInstanceOf<Runnable>()
            if (r != null) return r
            return receiveSuspend()
        }

        @Suppress("NOTHING_TO_INLINE")
        private suspend inline fun receiveSuspend(): Runnable? {
            do {
                val t = tasks.removeFirstIfIsInstanceOf<Runnable>()
                if (t != null) return t
                if (tasks.next is Poison) return null
                await()
            } while (true)
        }

        private val awaitSuspendBlock = { c: Continuation<Unit>? ->
            // nullable param is to avoid null check
            // we know that it is always non-null
            // and it will never crash if it is actually null
            val threadCont = ThreadCont
            if (!threadCont.compareAndSet(this, null, c)) throw IllegalStateException()
            if (tasks.next !== tasks && threadCont.compareAndSet(this, c, null)) Unit
            else COROUTINE_SUSPENDED
        }

        private suspend fun await() {
            return suspendCoroutineOrReturn(awaitSuspendBlock)
        }

        private fun run(task: Runnable) {
            try {
                task.run()
            } catch (t: Throwable) {
            }
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private val ThreadCont =
                AtomicReferenceFieldUpdater.newUpdater<IOThread, Continuation<*>>(IOThread::class.java, Continuation::class.java, IOThread::cont.name)
        as AtomicReferenceFieldUpdater<IOThread, Continuation<Unit>?>
    }

    private class Poison : LockFreeLinkedListNode()
    private class IODispatchedTask(val r: Runnable) : LockFreeLinkedListNode(), Runnable by r
}

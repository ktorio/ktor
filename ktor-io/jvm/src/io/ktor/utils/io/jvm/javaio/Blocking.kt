/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import java.io.*
import java.util.concurrent.locks.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Create blocking [java.io.InputStream] for this channel that does block every time the channel suspends at read
 * Similar to do reading in [runBlocking] however you can pass it to regular blocking API
 */
public fun ByteReadChannel.toInputStream(parent: Job? = null): InputStream = InputAdapter(parent, this)

/**
 * Create blocking [java.io.OutputStream] for this channel that does block every time the channel suspends at write
 * Similar to do reading in [runBlocking] however you can pass it to regular blocking API
 */
public fun ByteWriteChannel.toOutputStream(parent: Job? = null): OutputStream = OutputAdapter(parent, this)

private class InputAdapter(parent: Job?, private val channel: ByteReadChannel) : InputStream() {
    init {
        ensureParkingAllowed()
    }

    private val context = Job(parent)

    private val loop = object : BlockingAdapter(parent) {
        override suspend fun loop() {
            var rc = 0
            while (true) {
                val buffer = rendezvous(rc) as ByteArray
                rc = channel.readAvailable(buffer, offset, length)
                if (rc == -1) {
                    context.complete()
                    break
                }
            }
            finish(rc)
        }
    }

    private var single: ByteArray? = null

    override fun available(): Int {
        return channel.availableForRead
    }

    @Synchronized
    override fun read(): Int {
        val buffer = single ?: ByteArray(1).also { single = it }
        val rc = loop.submitAndAwait(buffer, 0, 1)
        if (rc == -1) return -1
        if (rc != 1) error("rc should be 1 or -1 but got $rc")
        return buffer[0].toInt() and 0xff
    }

    @Synchronized
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return loop.submitAndAwait(b!!, off, len)
    }

    @Synchronized
    override fun close() {
        super.close()
        channel.cancel()
        if (!context.isCompleted) {
            context.cancel()
        }
        loop.shutdown()
    }
}

private val CloseToken = Any()
private val FlushToken = Any()

private class OutputAdapter(parent: Job?, private val channel: ByteWriteChannel) : OutputStream() {
    init {
        ensureParkingAllowed()
    }

    private val loop = object : BlockingAdapter(parent) {
        override suspend fun loop() {
            try {
                while (true) {
                    val task = rendezvous(0)
                    if (task === CloseToken) {
                        break
                    } else if (task === FlushToken) {
                        channel.flush()
                        channel.closedCause?.let { throw it }
                    } else if (task is ByteArray) channel.writeFully(task, offset, length)
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    channel.close(t)
                }
                throw t
            } finally {
                if (!channel.close()) {
                    channel.closedCause?.let { throw it }
                }
            }
        }
    }

    private var single: ByteArray? = null

    @Synchronized
    override fun write(b: Int) {
        val buffer = single ?: ByteArray(1).also { single = it }
        buffer[0] = b.toByte()
        loop.submitAndAwait(buffer, 0, 1)
    }

    @Synchronized
    override fun write(b: ByteArray?, off: Int, len: Int) {
        loop.submitAndAwait(b!!, off, len)
    }

    @Synchronized
    override fun flush() {
        loop.submitAndAwait(FlushToken)
    }

    @Synchronized
    override fun close() {
        try {
            loop.submitAndAwait(CloseToken)
            loop.shutdown()
        } catch (t: Throwable) {
            throw IOException(t)
        }
    }
}

private abstract class BlockingAdapter(val parent: Job? = null) {

    private val end: Continuation<Unit> = object : Continuation<Unit> {
        override val context: CoroutineContext =
            if (parent != null) UnsafeBlockingTrampoline + parent else UnsafeBlockingTrampoline

        override fun resumeWith(result: Result<Unit>) {
            val value = result.exceptionOrNull() ?: Unit

            val before = state.getAndUpdate { current ->
                when (current) {
                    is Thread, is Continuation<*>, this -> value
                    else -> return
                }
            }

            when (before) {
                is Thread -> parkingImpl.unpark(before)
                is Continuation<*> -> result.exceptionOrNull()?.let {
                    before.resumeWithException(it)
                }
            }

            if (result.isFailure && result.exceptionOrNull() !is CancellationException) {
                parent?.cancel()
            }

            disposable?.dispose()
        }
    }

    @Suppress("LeakingThis")
    private val state: AtomicRef<Any> =
        atomic(this) // could be a thread, a continuation, Unit, an exception or this if not yet started
    private val result = atomic(0)
    private val disposable: DisposableHandle? = parent?.invokeOnCompletion { cause ->
        if (cause != null) {
            end.resumeWithException(cause)
        }
    }

    protected var offset: Int = 0
        private set
    protected var length: Int = 0
        private set

    init {
        val block: suspend () -> Unit = { loop() }
        block.startCoroutineUninterceptedOrReturn(end)
        require(state.value !== this)
    }

    protected abstract suspend fun loop()

    fun shutdown() {
        disposable?.dispose()
        end.resumeWithException(CancellationException("Stream closed"))
    }

    fun submitAndAwait(buffer: ByteArray, offset: Int, length: Int): Int {
        this.offset = offset
        this.length = length
        return submitAndAwait(buffer)
    }

    fun submitAndAwait(jobToken: Any): Int {
        val thread = Thread.currentThread()

        var cont: Continuation<Any>? = null

        state.update { value ->
            when (value) {
                is Continuation<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    cont = value as Continuation<Any>
                    thread
                }
                is Unit -> {
                    return result.value
                }
                is Throwable -> {
                    throw value
                }
                is Thread -> throw IllegalStateException("There is already thread owning adapter")
                this -> throw IllegalStateException("Not yet started")
                else -> NoWhenBranchMatchedException()
            }
        }

        cont!!.resume(jobToken)

        parkingLoop(thread)

        state.value.let { state ->
            if (state is Throwable) {
                throw state
            }
        }

        return result.value
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun parkingLoop(thread: Thread) {
        if (state.value !== thread) return

        do {
            val nextEventTimeNanos = processNextEventInCurrentThread()
            if (state.value !== thread) break

            if (nextEventTimeNanos > 0L) {
                parkingImpl.park(nextEventTimeNanos)
            }
        } while (true)
    }

    @Suppress("NOTHING_TO_INLINE")
    protected suspend inline fun rendezvous(rc: Int): Any {
        result.value = rc

        return suspendCoroutineUninterceptedOrReturn { ucont ->
            rendezvous(ucont)
        }
    }

    private fun rendezvous(ucont: Continuation<Any>): Any {
        var thread: Thread? = null

        state.update { value ->
            when (value) {
                is Thread -> {
                    thread = value
                    ucont.intercepted()
                }
                this -> ucont.intercepted()
                else -> throw IllegalStateException("Already suspended or in finished state")
            }
        }

        if (thread != null) {
            parkingImpl.unpark(thread!!)
        }

        return COROUTINE_SUSPENDED
    }

    protected fun finish(rc: Int) {
        result.value = rc
    }
}

private object UnsafeBlockingTrampoline : CoroutineDispatcher() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block.run()
    }
}

private fun ensureParkingAllowed() {
    if (!isParkingAllowed()) {
        error(
            "Using blocking primitives on this dispatcher is not allowed. " +
                "Consider using async channel instead or " +
                "use blocking primitives in withContext(Dispatchers.IO) instead."
        )
    }
}

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import org.apache.http.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

internal class ContentReader : CoroutineDispatcher(), Continuation<Unit> {
    private lateinit var currentDecoder: ContentDecoder
    private var control: IOControl? = null

    private val state: CompletableJob = Job()
    private var hasData = true

    private val _content = ByteChannel()

    val content: ByteReadChannel get() = _content

    private val events = ConcurrentLinkedQueue<Runnable>()

    override val context: CoroutineContext = this

    private val handler = suspend handler@{
        try {
            yield()
            if (!state.isActive) return@handler

            val decoder = currentDecoder
            var count = 0

            while (!_content.isClosedForWrite && state.isActive && !currentDecoder.isCompleted) {
                _content.write {
                    if (!state.isActive) return@write

                    count = decoder.read(it)
                    if (count == 0) {
                        hasData = false
                    }

                    if (count < 0) {
                        _content.close()
                        return@write
                    }
                }

                if (count == 0) {
                    yield()
                }
            }
        } catch (cause: ClosedChannelException) {
            _content.close()
        } catch (cause: Throwable) {
            _content.close(cause)
        } finally {
            hasData = false
            _content.close()
        }

        Unit
    }

    init {
        handler.startCoroutineUninterceptedOrReturn(this)
    }

    fun consume(decoder: ContentDecoder, ioControl: IOControl) {
        hasData = true
        if (state.isCompleted) return

        currentDecoder = decoder
        control = ioControl

        while (hasData && events.isNotEmpty()) {
            runNextEvent()
        }
    }

    /**
     * [Continuation]
     */
    override fun resumeWith(result: Result<Unit>) {}

    /**
     * [CoroutineDispatcher]
     */
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        events.add(block)
    }

    fun close(cause: Throwable? = null) {
        if (state.isCompleted) return
        if (cause == null) {
            state.complete()
        } else {
            state.completeExceptionally(cause)
        }

        while (events.isNotEmpty()) {
            runNextEvent()
        }
    }

    private inline fun runNextEvent() {
        val event = events.poll() ?: return
        try {
            event.run()
        } catch (cause: Throwable) { }
    }
}

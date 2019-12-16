/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Event loop dispatcher having a queue of the specified [queueSize].
 * Unlike a plain queue-based event dispatcher, this one does resume input interest on dispatch.
 */
internal class ReactorLoopDispatcher(
    private val interestController: InterestControllerHolder,
    private val queueSize: Int
) : CoroutineDispatcher() {
    private val queue = ArrayBlockingQueue<Runnable>(queueSize)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        check(queue.add(block)) { "Dispatcher queue of size $queueSize is full: $queue" }
        interestController.resumeInputIfPossible()
    }

    /**
     * This should be only invoked from a reactor thread.
     */
    fun processLoop() {
        while (true) {
            queue.poll()?.run() ?: break
        }
    }

    /**
     * Check if there are queued tasks.
     */
    fun hasTasks(): Boolean = queue.isNotEmpty()
}

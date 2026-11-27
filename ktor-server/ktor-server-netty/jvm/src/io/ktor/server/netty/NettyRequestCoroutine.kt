/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import kotlinx.coroutines.*
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The coroutine that handles one request.
 *
 * It is at the same time the `Job` element of the call's coroutine context and the coroutine that runs the engine
 * pipeline, so a request costs a single job object instead of a `Job` plus a launched coroutine. Its parent is the
 * `Job` found in [parentContext] (the connection-level supervisor): cancelling the application cancels every
 * in-flight request, and joining it waits for their bodies to unwind, including children launched in the call scope.
 *
 * Unlike a plain `Job`, cancellation can never complete this job before its body has run: [launchOn] always
 * starts the body, so `finally` blocks and `NonCancellable` cleanup are guaranteed to execute.
 */
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal class NettyRequestCoroutine(parentContext: CoroutineContext) :
    AbstractCoroutine<Unit>(
        CoroutineScope(parentContext).newCoroutineContext(EmptyCoroutineContext),
        initParentJob = true,
        active = true
    ),
    Runnable {

    private var block: (suspend CoroutineScope.() -> Unit)? = null

    /**
     * Schedules [block] to run undispatched on [executor]. Must be called at most once.
     * If [executor] rejects the task, the exception propagates and this job stays incomplete: the caller must
     * complete it with [resumeWith].
     */
    fun launchOn(executor: Executor, block: suspend CoroutineScope.() -> Unit) {
        this.block = block
        executor.execute(this)
    }

    override fun run() {
        val block = checkNotNull(block) { "Request coroutine has already been started" }
        this.block = null
        start(CoroutineStart.UNDISPATCHED, this, block)
    }

    override fun handleJobException(exception: Throwable): Boolean {
        handleCoroutineException(context, exception)
        return true
    }
}

/**
 * Maps a failure that happened before a request coroutine could start into its completion cause. A rejected
 * dispatch means the call executor is shutting down: the call is cancelled rather than failed, so the connection
 * supervisor is not told about a request error that is just the server going away.
 */
internal fun Throwable.asCallCompletion(): Throwable = when (this) {
    is RejectedExecutionException -> CancellationException("Call executor is shut down", this)
    else -> this
}

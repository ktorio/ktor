/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.pipeline

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Represents running execution of a pipeline
 */
@ContextDsl
interface PipelineContext<TSubject : Any, TContext : Any> : CoroutineScope {
    /**
     * Object representing context in which pipeline executes
     */
    val context: TContext

    /**
     * Subject of this pipeline execution that goes along the pipeline
     */
    val subject: TSubject

    /**
     * Finishes current pipeline execution
     */
    fun finish()

    /**
     * Continues execution of the pipeline with the given subject
     */
    suspend fun proceedWith(subject: TSubject): TSubject

    /**
     * Continues execution of the pipeline with the same subject
     */
    suspend fun proceed(): TSubject
}

/**
 * Represent an object that launches pipeline execution
 */
@KtorExperimentalAPI
interface PipelineExecutor<R> {
    /**
     * Start pipeline execution or fail if already running and not yet completed.
     * It should not be invoked concurrently.
     */
    suspend fun execute(initial: R): R
}

/**
 * Build a pipeline of the specified [interceptors] and create executor
 */
@KtorExperimentalAPI
fun <TSubject : Any, TContext : Any> pipelineExecutorFor(
    context: TContext,
    interceptors: List<PipelineInterceptor<TSubject, TContext>>,
    subject: TSubject
): PipelineExecutor<TSubject> {
    return SuspendFunctionGun(subject, context, interceptors)
}

private class SuspendFunctionGun<TSubject : Any, TContext : Any>(
    initial: TSubject,
    override val context: TContext,
    private val blocks: List<PipelineInterceptor<TSubject, TContext>>
) : PipelineContext<TSubject, TContext>, PipelineExecutor<TSubject>, CoroutineScope {

    override val coroutineContext: CoroutineContext get() = continuation.context

    // Stack-walking state
    private var lastPeekedIndex = -1

    // this is impossible to inline because of property name clash
    // between PipelineContext.context and Continuation.context
    private val continuation: Continuation<Unit> = object : Continuation<Unit>, CoroutineStackFrame {
        override val callerFrame: CoroutineStackFrame? get() = peekContinuation() as? CoroutineStackFrame

        override fun getStackTraceElement(): StackTraceElement? = null

        private fun peekContinuation(): Continuation<*>? {
            if (lastPeekedIndex < 0) return null
            val rootContinuation = rootContinuation

            @Suppress("UNCHECKED_CAST")
            when (rootContinuation) {
                null -> return null
                is Continuation<*> -> {
                    --lastPeekedIndex
                    return rootContinuation
                }
                is ArrayList<*> -> {
                    if (rootContinuation.isEmpty()) return null
                    return rootContinuation[lastPeekedIndex--] as Continuation<*>
                }
                else -> return null
            }
        }

        @Suppress("UNCHECKED_CAST")
        override val context: CoroutineContext
            get () {
                val cont = rootContinuation
                return when (cont) {
                    null -> throw IllegalStateException("Not started")
                    is Continuation<*> -> cont.context
                    is List<*> -> (cont as List<Continuation<*>>).last().context
                    else -> throw IllegalStateException("Unexpected rootContinuation value")
                }
            }

        override fun resumeWith(result: Result<Unit>) {
            if (result.isFailure) {
                resumeRootWith(Result.failure(result.exceptionOrNull()!!))
                return
            }

            loop(false)
        }
    }

    override var subject: TSubject = initial
        private set

    private var rootContinuation: Any? = null
    private var index = 0

    override fun finish() {
        index = blocks.size
    }

    override suspend fun proceed(): TSubject = suspendCoroutineUninterceptedOrReturn { continuation ->
        if (index == blocks.size) return@suspendCoroutineUninterceptedOrReturn subject

        addContinuation(continuation)

        if (loop(true)) {
            discardLastRootContinuation()
            return@suspendCoroutineUninterceptedOrReturn subject
        }

        COROUTINE_SUSPENDED
    }

    override suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    override suspend fun execute(initial: TSubject): TSubject {
        index = 0
        if (index == blocks.size) return initial
        subject = initial

        if (rootContinuation != null) throw IllegalStateException("Already started")

        return proceed()
    }

    /**
     * @return `true` if it is possible to return result immediately
     */
    private fun loop(direct: Boolean): Boolean {
        do {
            val index = index  // it is important to read index every time
            if (index == blocks.size) {
                if (!direct) {
                    resumeRootWith(Result.success(subject))
                    return false
                }

                return true
            }

            this@SuspendFunctionGun.index = index + 1  // it is important to increase it before function invocation
            val next = blocks[index]

            try {
                val me = this@SuspendFunctionGun

                val rc = next.startCoroutineUninterceptedOrReturn3(me, me.subject, me.continuation)
                if (rc === COROUTINE_SUSPENDED) {
                    return false
                }
            } catch (cause: Throwable) {
                resumeRootWith(Result.failure(cause))
                return false
            }
        } while (true)
    }

    private fun resumeRootWith(result: Result<TSubject>) {
        val rootContinuation = rootContinuation

        @Suppress("UNCHECKED_CAST")
        val next = when (rootContinuation) {
            null -> throw IllegalStateException("No more continuations to resume")
            is Continuation<*> -> {
                this.rootContinuation = null
                lastPeekedIndex = -1
                rootContinuation
            }
            is ArrayList<*> -> {
                if (rootContinuation.isEmpty()) throw IllegalStateException("No more continuations to resume")
                lastPeekedIndex = rootContinuation.lastIndex - 1
                rootContinuation.removeAt(rootContinuation.lastIndex)
            }
            else -> unexpectedRootContinuationValue(rootContinuation)
        } as Continuation<TSubject>

        next.resumeWith(result)
    }

    private fun discardLastRootContinuation() {
        val rootContinuation = rootContinuation

        @Suppress("UNCHECKED_CAST")
        when (rootContinuation) {
            null -> throw IllegalStateException("No more continuations to resume")
            is Continuation<*> -> {
                lastPeekedIndex = -1
                this.rootContinuation = null
            }
            is ArrayList<*> -> {
                if (rootContinuation.isEmpty()) throw IllegalStateException("No more continuations to resume")
                rootContinuation.removeAt(rootContinuation.lastIndex)
                lastPeekedIndex = rootContinuation.lastIndex
            }
            else -> unexpectedRootContinuationValue(rootContinuation)
        }
    }

    private fun addContinuation(continuation: Continuation<TSubject>) {
        val rootContinuation = rootContinuation

        when (rootContinuation) {
            null -> {
                lastPeekedIndex = 0
                this.rootContinuation = continuation
            }
            is Continuation<*> -> {
                this.rootContinuation = ArrayList<Continuation<*>>(blocks.size).apply {
                    add(rootContinuation)
                    add(continuation)
                    lastPeekedIndex = 1
                }
            }
            is ArrayList<*> -> {
                @Suppress("UNCHECKED_CAST")
                rootContinuation as ArrayList<Continuation<TSubject>>
                rootContinuation.add(continuation)
                lastPeekedIndex = rootContinuation.lastIndex
            }
            else -> unexpectedRootContinuationValue(rootContinuation)
        }
    }

    private fun unexpectedRootContinuationValue(rootContinuation: Any?): Nothing {
        throw IllegalStateException("Unexpected rootContinuation content: $rootContinuation")
    }
}

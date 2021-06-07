/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Represents running execution of a pipeline
 */
@ContextDsl
public interface PipelineContext<TSubject : Any, TContext : Any> : CoroutineScope {
    /**
     * Object representing context in which pipeline executes
     */
    public val context: TContext

    /**
     * Subject of this pipeline execution that goes along the pipeline
     */
    public val subject: TSubject

    /**
     * Finishes current pipeline execution
     */
    public fun finish()

    /**
     * Continues execution of the pipeline with the given subject
     */
    public suspend fun proceedWith(subject: TSubject): TSubject

    /**
     * Continues execution of the pipeline with the same subject
     */
    public suspend fun proceed(): TSubject
}

/**
 * Represent an object that launches pipeline execution
 */
@Deprecated("This is going to become internal. Use Pipeline.execute() instead.")
public interface PipelineExecutor<R> {
    /**
     * Start pipeline execution or fail if already running and not yet completed.
     * It should not be invoked concurrently.
     */
    public suspend fun execute(initial: R): R
}

/**
 * Build a pipeline of the specified [interceptors] and create executor.
 */
@Deprecated("This is going to become internal. Use Pipeline.execute() instead.")
public fun <TSubject : Any, TContext : Any> pipelineExecutorFor(
    context: TContext,
    interceptors: List<PipelineInterceptor<TSubject, TContext>>,
    subject: TSubject,
):
    @Suppress("DEPRECATION")
    PipelineExecutor<TSubject> = SuspendFunctionGun(subject, context, interceptors)

/**
 * Build a pipeline of the specified [interceptors] and create executor.
 */
internal fun <TSubject : Any, TContext : Any> pipelineExecutorFor(
    context: TContext,
    interceptors: List<PipelineInterceptor<TSubject, TContext>>,
    subject: TSubject,
    coroutineContext: CoroutineContext,
    debugMode: Boolean = false
):
    @Suppress("DEPRECATION")
    PipelineExecutor<TSubject> = if (debugMode) {
        DebugPipelineContext(context, interceptors, subject, coroutineContext)
    } else {
        SuspendFunctionGun(subject, context, interceptors)
    }

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import io.ktor.utils.io.concurrent.*
import kotlinx.atomicfu.*
import kotlin.coroutines.*

/**
 * Represents an execution pipeline for asynchronous extensible computations
 */
public open class Pipeline<TSubject : Any, TContext : Any>(
    vararg phases: PipelinePhase
) {
    /**
     * Provides common place to store pipeline attributes
     */
    public val attributes: Attributes = Attributes(concurrent = true)

    /**
     * Indicated if debug mode is enabled. In debug mode users will get more details in the stacktrace.
     */
    public open val developmentMode: Boolean = false

    private val phasesRaw: MutableList<Any> = sharedListOf(*phases)

    private var interceptorsQuantity by shared(0)

    /**
     * Phases of this pipeline
     */
    public val items: List<PipelinePhase>
        get() = phasesRaw.map {
            it as? PipelinePhase ?: (it as? PhaseContent<*, *>)?.phase!!
        }

    /**
     * @return `true` if there are no interceptors installed regardless number of phases
     */
    @InternalAPI
    public val isEmpty: Boolean
        get() = interceptorsQuantity == 0

    private val _interceptors: AtomicRef<List<suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit>?> =
        atomic(null)

    private var interceptors: List<PipelineInterceptor<TSubject, TContext>>?
        get() = _interceptors.value
        set(value) {
            _interceptors.value = value
        }

    /**
     * share between pipelines/contexts
     */
    private var interceptorsListShared: Boolean by shared(false)

    /**
     * interceptors list is shared with pipeline phase content
     */
    private var interceptorsListSharedPhase: PipelinePhase? by shared(null)

    public constructor(
        phase: PipelinePhase,
        interceptors: List<PipelineInterceptor<TSubject, TContext>>
    ) : this(phase) {
        interceptors.forEach { intercept(phase, it) }
    }

    /**
     * Executes this pipeline in the given [context] and with the given [subject]
     */
    public suspend fun execute(context: TContext, subject: TSubject): TSubject =
        createContext(context, subject, coroutineContext).execute(subject)

    /**
     * Adds [phase] to the end of this pipeline
     */
    public fun addPhase(phase: PipelinePhase) {
        if (hasPhase(phase)) {
            return
        }

        phasesRaw.add(phase)
    }

    /**
     * Inserts [phase] after the [reference] phase
     */
    public fun insertPhaseAfter(reference: PipelinePhase, phase: PipelinePhase) {
        if (hasPhase(phase)) return

        val index = findPhaseIndex(reference)
        if (index == -1) {
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        }

        phasesRaw.add(index + 1, PhaseContent<TSubject, TContext>(phase, PipelinePhaseRelation.After(reference)))
    }

    /**
     * Inserts [phase] before the [reference] phase
     */
    public fun insertPhaseBefore(reference: PipelinePhase, phase: PipelinePhase) {
        if (hasPhase(phase)) return

        val index = findPhaseIndex(reference)
        if (index == -1) {
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        }

        phasesRaw.add(index, PhaseContent<TSubject, TContext>(phase, PipelinePhaseRelation.Before(reference)))
    }

    /**
     * Adds [block] to the [phase] of this pipeline
     */
    public fun intercept(phase: PipelinePhase, block: PipelineInterceptor<TSubject, TContext>) {
        val phaseContent = findPhase(phase)
            ?: throw InvalidPhaseException("Phase $phase was not registered for this pipeline")

        if (tryAddToPhaseFastPath(phase, block)) {
            interceptorsQuantity++
            return
        }

        phaseContent.addInterceptor(block)
        interceptorsQuantity++
        resetInterceptorsList()

        afterIntercepted()
    }

    /**
     * Invoked after an interceptor has been installed
     */
    public open fun afterIntercepted() {
    }

    /**
     * Merges another pipeline into this pipeline, maintaining relative phases order
     */
    public fun merge(from: Pipeline<TSubject, TContext>) {
        if (fastPathMerge(from)) {
            return
        }

        if (interceptorsQuantity == 0) {
            setInterceptorsListFromAnotherPipeline(from)
        } else {
            resetInterceptorsList()
        }

        val fromPhases = from.phasesRaw
        for (index in 0..fromPhases.lastIndex) {
            val fromPhaseOrContent = fromPhases[index]

            val fromPhase =
                (fromPhaseOrContent as? PipelinePhase) ?: (fromPhaseOrContent as PhaseContent<*, *>).phase

            if (!hasPhase(fromPhase)) {
                val fromPhaseRelation = when {
                    fromPhaseOrContent === fromPhase -> PipelinePhaseRelation.Last
                    else -> (fromPhaseOrContent as PhaseContent<*, *>).relation
                }

                when (fromPhaseRelation) {
                    is PipelinePhaseRelation.Last -> addPhase(fromPhase)
                    is PipelinePhaseRelation.Before -> insertPhaseBefore(
                        fromPhaseRelation.relativeTo,
                        fromPhase
                    )
                    is PipelinePhaseRelation.After -> insertPhaseAfter(
                        fromPhaseRelation.relativeTo,
                        fromPhase
                    )
                }
            }

            if (fromPhaseOrContent is PhaseContent<*, *> && !fromPhaseOrContent.isEmpty) {
                @Suppress("UNCHECKED_CAST")
                fromPhaseOrContent as PhaseContent<TSubject, TContext>

                fromPhaseOrContent.addTo(findPhase(fromPhase)!!)
                interceptorsQuantity += fromPhaseOrContent.size
            }
        }
    }

    internal fun phaseInterceptors(phase: PipelinePhase): List<PipelineInterceptor<TSubject, TContext>> =
        findPhase(phase)?.sharedInterceptors() ?: emptyList()

    /**
     * For tests only
     */
    internal fun interceptorsForTests(): List<PipelineInterceptor<TSubject, TContext>> {
        return interceptors ?: cacheInterceptors()
    }

    @Suppress("DEPRECATION")
    private fun createContext(
        context: TContext,
        subject: TSubject,
        coroutineContext: CoroutineContext
    ): PipelineExecutor<TSubject> =
        pipelineExecutorFor(context, sharedInterceptorsList(), subject, coroutineContext, developmentMode)

    private fun findPhase(phase: PipelinePhase): PhaseContent<TSubject, TContext>? {
        val phasesList = phasesRaw

        for (index in 0 until phasesList.size) {
            val current = phasesList[index]
            if (current === phase) {
                val content = PhaseContent<TSubject, TContext>(phase, PipelinePhaseRelation.Last)
                phasesList[index] = content
                return content
            }

            if (current is PhaseContent<*, *> && current.phase === phase) {
                @Suppress("UNCHECKED_CAST")
                return current as PhaseContent<TSubject, TContext>
            }
        }

        return null
    }

    private fun findPhaseIndex(phase: PipelinePhase): Int {
        val phasesList = phasesRaw
        for (index in 0 until phasesList.size) {
            val current = phasesList[index]
            if (current === phase || (current is PhaseContent<*, *> && current.phase === phase)) {
                return index
            }
        }

        return -1
    }

    private fun hasPhase(phase: PipelinePhase): Boolean {
        val phasesList = phasesRaw
        for (index in 0 until phasesList.size) {
            val current = phasesList[index]
            if (current === phase || (current is PhaseContent<*, *> && current.phase === phase)) {
                return true
            }
        }

        return false
    }

    private fun cacheInterceptors(): List<PipelineInterceptor<TSubject, TContext>> {
        val interceptorsQuantity = interceptorsQuantity
        if (interceptorsQuantity == 0) {
            notSharedInterceptorsList(emptyList())
            return emptyList()
        }

        val phases = phasesRaw
        if (interceptorsQuantity == 1) {
            for (phaseIndex in 0..phases.lastIndex) {
                @Suppress("UNCHECKED_CAST")
                val phaseContent =
                    phases[phaseIndex] as? PhaseContent<TSubject, TContext> ?: continue

                if (!phaseContent.isEmpty) {
                    val interceptors = phaseContent.sharedInterceptors()
                    setInterceptorsListFromPhase(phaseContent)
                    return interceptors
                }
            }
        }

        val destination: MutableList<suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit> = sharedListOf()
        for (phaseIndex in 0..phases.lastIndex) {
            @Suppress("UNCHECKED_CAST")
            val phase = phases[phaseIndex] as? PhaseContent<TSubject, TContext>
                ?: continue

            phase.addTo(destination)
        }

        notSharedInterceptorsList(destination)
        return destination
    }

    private fun fastPathMerge(from: Pipeline<TSubject, TContext>): Boolean {
        if (from.phasesRaw.isEmpty()) {
            return true
        }

        if (phasesRaw.isNotEmpty()) {
            return false
        }

        val fromPhases = from.phasesRaw

        for (index in 0..fromPhases.lastIndex) {
            val fromPhaseOrContent = fromPhases[index]
            if (fromPhaseOrContent is PipelinePhase) {
                phasesRaw.add(fromPhaseOrContent)
                continue
            }

            if (fromPhaseOrContent is PhaseContent<*, *>) {
                @Suppress("UNCHECKED_CAST")
                fromPhaseOrContent as PhaseContent<TSubject, TContext>

                phasesRaw.add(
                    PhaseContent(
                        fromPhaseOrContent.phase,
                        fromPhaseOrContent.relation,
                        fromPhaseOrContent.sharedInterceptors()
                    )
                )
                continue
            }
        }

        interceptorsQuantity += from.interceptorsQuantity
        setInterceptorsListFromAnotherPipeline(from)
        return true
    }

    private fun sharedInterceptorsList(): List<PipelineInterceptor<TSubject, TContext>> {
        if (interceptors == null) {
            cacheInterceptors()
        }

        interceptorsListShared = true
        return interceptors!!
    }

    private fun resetInterceptorsList() {
        interceptors = null
        interceptorsListShared = false
        interceptorsListSharedPhase = null
    }

    private fun notSharedInterceptorsList(list: List<PipelineInterceptor<TSubject, TContext>>) {
        interceptors = list
        interceptorsListShared = false
        interceptorsListSharedPhase = null
    }

    private fun setInterceptorsListFromPhase(phaseContent: PhaseContent<TSubject, TContext>) {
        interceptors = phaseContent.sharedInterceptors()
        interceptorsListShared = false
        interceptorsListSharedPhase = phaseContent.phase
    }

    private fun setInterceptorsListFromAnotherPipeline(pipeline: Pipeline<TSubject, TContext>) {
        interceptors = pipeline.sharedInterceptorsList()
        interceptorsListShared = true
        interceptorsListSharedPhase = null
    }

    private fun tryAddToPhaseFastPath(phase: PipelinePhase, block: PipelineInterceptor<TSubject, TContext>): Boolean {
        val currentInterceptors = interceptors
        if (phasesRaw.isEmpty() || currentInterceptors == null) {
            return false
        }

        if (interceptorsListShared || currentInterceptors !is MutableList) {
            return false
        }

        if (interceptorsListSharedPhase == phase) {
            currentInterceptors.add(block)
            return true
        }

        if (phase == phasesRaw.last() || findPhaseIndex(phase) == phasesRaw.lastIndex) {
            findPhase(phase)!!.addInterceptor(block)
            currentInterceptors.add(block)
            return true
        }

        return false
    }
}

/**
 * Executes this pipeline
 */
@Suppress("NOTHING_TO_INLINE")
public suspend inline fun <TContext : Any> Pipeline<Unit, TContext>.execute(
    context: TContext
): Unit = execute(context, Unit)

/**
 * Intercepts an untyped pipeline when the subject is of the given type
 */
public inline fun <reified TSubject : Any, TContext : Any> Pipeline<*, TContext>.intercept(
    phase: PipelinePhase,
    noinline block: suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit
) {
    intercept(phase) interceptor@{ subject ->
        if (subject !is TSubject) return@interceptor

        @Suppress("UNCHECKED_CAST")
        val reinterpret = this as? PipelineContext<TSubject, TContext>
        reinterpret?.block(subject)
    }
}

/**
 * Represents an interceptor type which is a suspend extension function for context
 */
public typealias PipelineInterceptor<TSubject, TContext> =
    suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit

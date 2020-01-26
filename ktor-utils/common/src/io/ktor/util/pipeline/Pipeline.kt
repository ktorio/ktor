/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.pipeline

import io.ktor.util.*

/**
 * Represents an execution pipeline for asynchronous extensible computations
 */
open class Pipeline<TS : Any, TC : Any>(vararg phases: PipelinePhase) {

    /**
     * Provides a common place to store pipeline attributes
     */
    val attributes = Attributes()

    private sealed class PhaseRelation {
        class After(val reference: PipelinePhase) : PhaseRelation()
        class Before(val reference: PipelinePhase) : PhaseRelation()
        object End : PhaseRelation()
    }

    // each phase must be present in both [phaseList] and [phaseMap]
    private val phaseList: MutableList<PipelinePhase> = arrayListOf()
    private val phaseMap: MutableMap<PipelinePhase, PhaseRelation> = hashMapOf()
    // a phase is only present in [phaseInterceptors] if its list of interceptors is non-empty
    private val phaseInterceptors: MutableMap<PipelinePhase, MutableList<PipelineInterceptor<TS, TC>>> = hashMapOf()
    // caching of the total number of interceptors and the complete list of interceptors
    private var cachedInterceptors: List<PipelineInterceptor<TS, TC>>? = null
    private var interceptorsCount = 0

    init {
        phases.forEach { addPhase(it) }
    }

    constructor(phase: PipelinePhase, interceptors: List<PipelineInterceptor<TS, TC>>) : this() {
        insertPhase(phase, PhaseRelation.End, interceptors)
    }

    /**
     * Phases of this pipeline
     */
    val items: List<PipelinePhase>
        get() = phaseList

    /**
     * @return `true` if there are no interceptors installed regardless of the number of phases
     */
    @InternalAPI
    val isEmpty: Boolean
        get() = interceptorsCount == 0

    /**
     * Executes this pipeline in the given [context] and with the given [subject]
     */
    suspend fun execute(context: TC, subject: TS): TS =
        pipelineExecutorFor(context, allInterceptors(), subject).execute(subject)

    internal fun allInterceptors(): List<PipelineInterceptor<TS, TC>> {
        if (cachedInterceptors === null) {
            val cache = ArrayList<PipelineInterceptor<TS, TC>>(interceptorsCount)
            phaseList.forEach { phase -> phaseInterceptors[phase]?.also { cache.addAll(it) } }
            cachedInterceptors = cache
        }
        return cachedInterceptors!!
    }

    /**
     * Adds [phase] to the end of this pipeline
     */
    fun addPhase(phase: PipelinePhase) {
        if (hasPhase(phase)) return
        insertPhase(phase, PhaseRelation.End, emptyList())
    }

    /**
     * Inserts [phase] after the [reference] phase
     */
    fun insertPhaseAfter(reference: PipelinePhase, phase: PipelinePhase) {
        checkHasPhaseOrThrow(reference)
        if (hasPhase(phase)) return
        insertPhase(phase, PhaseRelation.After(reference), emptyList())
    }

    /**
     * Inserts [phase] before the [reference] phase
     */
    fun insertPhaseBefore(reference: PipelinePhase, phase: PipelinePhase) {
        checkHasPhaseOrThrow(reference)
        if (hasPhase(phase)) return
        insertPhase(phase, PhaseRelation.Before(reference), emptyList())
    }

    private fun hasPhase(phase: PipelinePhase) = phaseMap.containsKey(phase)

    private fun checkHasPhaseOrThrow(phase: PipelinePhase) {
        if (!hasPhase(phase)) {
            throw InvalidPhaseException("Phase $phase was not registered for this pipeline")
        }
    }

    private fun phaseIndex(phase: PipelinePhase): Int? {
        val index = phaseList.indexOf(phase)
        return if (index >= 0) index else null
    }

    private fun insertPhase(
        phase: PipelinePhase,
        relation: PhaseRelation,
        interceptors: List<PipelineInterceptor<TS, TC>>
    ) {
        if (hasPhase(phase)) {
            throw IllegalStateException()
        }
        val insertionIndex = when (relation) {
            is PhaseRelation.After -> phaseIndex(relation.reference)?.plus(1)
            is PhaseRelation.Before -> phaseIndex(relation.reference)
            is PhaseRelation.End -> null
        }
        if (insertionIndex === null) {
            phaseList.add(phase)
        } else {
            phaseList.add(insertionIndex, phase)
        }
        phaseMap[phase] = relation
        insertInterceptors(phase, interceptors)
    }

    private fun insertInterceptors(
        phase: PipelinePhase,
        interceptors: List<PipelineInterceptor<TS, TC>>
    ) {
        if (!hasPhase(phase)) {
            throw IllegalStateException()
        }
        if (interceptors.isEmpty()) {
            return
        }
        phaseInterceptors.getOrPut(phase) { arrayListOf() }.addAll(interceptors)
        cachedInterceptors = null
        interceptorsCount += interceptors.size
    }

    /**
     * Merges another pipeline into this pipeline, maintaining relative phases order
     */
    fun merge(from: Pipeline<TS, TC>) {
        if (phaseList.isEmpty()) {
            // fast path
            sync(from)
        } else {
            // normal path
            from.phaseList.forEach { fromPhase ->
                val fromInterceptors: List<PipelineInterceptor<TS, TC>> =
                    from.phaseInterceptors[fromPhase] ?: emptyList()
                if (hasPhase(fromPhase)) {
                    insertInterceptors(fromPhase, fromInterceptors)
                } else {
                    val fromRelation = from.phaseMap[fromPhase]!!
                    insertPhase(fromPhase, fromRelation, fromInterceptors)
                }
            }
        }
    }

    private fun sync(from: Pipeline<TS, TC>) {
        if (phaseList.isNotEmpty() || phaseMap.isNotEmpty() || phaseInterceptors.isNotEmpty() ||
            cachedInterceptors !== null || interceptorsCount != 0
        ) {
            throw IllegalStateException()
        }
        phaseList.addAll(from.phaseList)
        phaseMap.putAll(from.phaseMap)
        phaseInterceptors.putAll(from.phaseInterceptors)
        cachedInterceptors = from.cachedInterceptors
        interceptorsCount = from.interceptorsCount
    }

    /**
     * Adds [block] to the [phase] of this pipeline
     */
    fun intercept(phase: PipelinePhase, block: PipelineInterceptor<TS, TC>) {
        checkHasPhaseOrThrow(phase)
        insertInterceptors(phase, listOf(block))
        afterIntercepted()
    }

    /**
     * Invoked after an interceptor has been installed
     */
    protected open fun afterIntercepted() {
        // no-op
    }

}

/**
 * Executes this pipeline
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun <TContext : Any> Pipeline<Unit, TContext>.execute(context: TContext): Unit = execute(context, Unit)

/**
 * Intercepts an untyped pipeline when the subject is of the given type
 */
inline fun <reified TSubject : Any, TContext : Any> Pipeline<*, TContext>.intercept(
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
typealias PipelineInterceptor<TSubject, TContext> = suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit

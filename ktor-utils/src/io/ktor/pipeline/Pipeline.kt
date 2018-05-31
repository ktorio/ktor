package io.ktor.pipeline

import io.ktor.util.*
//import kotlin.jvm.*

/**
 * Represents an execution pipeline for asynchronous extensible computations
 */
open class Pipeline<TSubject : Any, TContext : Any>(vararg phases: PipelinePhase) {
    /**
     * Provides common place to store pipeline attributes
     */
    val attributes = Attributes()

    constructor(phase: PipelinePhase, interceptors: List<PipelineInterceptor<TSubject, TContext>>) : this(phase) {
        interceptors.forEach { intercept(phase, it) }
    }

    /**
     * Executes this pipeline in the given [context] and with the given [subject]
     */
    suspend fun execute(context: TContext, subject: TSubject): TSubject =
        PipelineContext(context, interceptors(), subject).proceed()

    private class PhaseContent<TSubject : Any, Call : Any>(
        val phase: PipelinePhase,
        val relation: PipelinePhaseRelation,
        val interceptors: ArrayList<PipelineInterceptor<TSubject, Call>>
    ) {
        override fun toString(): String = "Phase `${phase.name}`, ${interceptors.size} handlers"
    }

    /**
     * Represents relations between pipeline phases
     */
    private sealed class PipelinePhaseRelation {
        /**
         * Given phase should be executed after [relativeTo] phase
         * @property relativeTo represents phases for relative positioning
         */
        class After(val relativeTo: PipelinePhase) : PipelinePhaseRelation()

        /**
         * Given phase should be executed before [relativeTo] phase
         * @property relativeTo represents phases for relative positioning
         */
        class Before(val relativeTo: PipelinePhase) : PipelinePhaseRelation()

        /**
         * Given phase should be executed last
         */
        object Last : PipelinePhaseRelation()
    }

    private val phases = phases.mapTo(ArrayList<PhaseContent<TSubject, TContext>>(phases.size)) {
        PhaseContent(it, PipelinePhaseRelation.Last, arrayListOf())
    }

    private var interceptorsQuantity = 0

//    @Volatile
    private var interceptors: ArrayList<PipelineInterceptor<TSubject, TContext>>? = null

    /**
     * Phases of this pipeline
     */
    val items: List<PipelinePhase> get() = phases.map { it.phase }

    /**
     * Adds [phase] to the end of this pipeline
     */
    fun addPhase(phase: PipelinePhase) {
        if (phases.any { it.phase == phase }) return
        phases.add(PhaseContent(phase, PipelinePhaseRelation.Last, arrayListOf()))
        interceptors = null
    }

    /**
     * Inserts [phase] after the [reference] phase
     */
    fun insertPhaseAfter(reference: PipelinePhase, phase: PipelinePhase) {
        if (phases.any { it.phase == phase }) return
        val index = phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        phases.add(index + 1, PhaseContent(phase, PipelinePhaseRelation.After(reference), arrayListOf()))
        interceptors = null
    }

    /**
     * Inserts [phase] before the [reference] phase
     */
    fun insertPhaseBefore(reference: PipelinePhase, phase: PipelinePhase) {
        if (phases.any { it.phase == phase }) return
        val index = phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        phases.add(index, PhaseContent(phase, PipelinePhaseRelation.Before(reference), arrayListOf()))
        interceptors = null
    }

    private fun interceptors(): ArrayList<PipelineInterceptor<TSubject, TContext>> {
        return interceptors ?: cacheInterceptors()
    }

    private fun cacheInterceptors(): ArrayList<PipelineInterceptor<TSubject, TContext>> {
        val destination = ArrayList<PipelineInterceptor<TSubject, TContext>>(interceptorsQuantity)
        for (phaseIndex in 0..phases.lastIndex) {
            val elements = phases[phaseIndex].interceptors
            for (elementIndex in 0..elements.lastIndex) {
                destination.add(elements[elementIndex])
            }
        }
        interceptors = destination
        return destination
    }

    /**
     * Adds [block] to the [phase] of this pipeline
     */
    open fun intercept(phase: PipelinePhase, block: PipelineInterceptor<TSubject, TContext>) {
        val phaseContent = phases.firstOrNull { it.phase == phase }
                ?: throw InvalidPhaseException("Phase $phase was not registered for this pipeline")

        phaseContent.interceptors.add(block)
        interceptorsQuantity++
        interceptors = null
    }

    companion object {
        private fun <TSubject : Any, Call : Any> ArrayList<PhaseContent<TSubject, Call>>.findPhase(phase: PipelinePhase): PhaseContent<TSubject, Call>? {
            @Suppress("LoopToCallChain")
            for (index in 0..lastIndex) {
                val localPhase = get(index)
                if (localPhase.phase == phase)
                    return localPhase
            }
            return null
        }
    }

    /**
     * Merges another pipeline into this pipeline, maintaining relative phases order
     */
    fun merge(from: Pipeline<TSubject, TContext>) {
        if (from.phases.isEmpty())
            return
        if (phases.isEmpty()) {
            val fromPhases = from.phases
            @Suppress("LoopToCallChain")
            for (index in 0..fromPhases.lastIndex) {
                val fromContent = fromPhases[index]
                val interceptors = ArrayList<PipelineInterceptor<TSubject, TContext>>(fromContent.interceptors)
                phases.add(PhaseContent(fromContent.phase, fromContent.relation, interceptors))
            }
            interceptorsQuantity += from.interceptorsQuantity
            interceptors = null
            return
        }

        val fromPhases = from.phases
        for (index in 0..fromPhases.lastIndex) {
            val fromContent = fromPhases[index]
            val phaseContent = phases.findPhase(fromContent.phase) ?: run {
                when (fromContent.relation) {
                    is PipelinePhaseRelation.Last -> addPhase(fromContent.phase)
                    is PipelinePhaseRelation.Before -> insertPhaseBefore(
                        fromContent.relation.relativeTo,
                        fromContent.phase
                    )
                    is PipelinePhaseRelation.After -> insertPhaseAfter(
                        fromContent.relation.relativeTo,
                        fromContent.phase
                    )
                }
                phases.first { it.phase == fromContent.phase }
            }
            phaseContent.interceptors.addAll(fromContent.interceptors)
            interceptorsQuantity += fromContent.interceptors.size
        }
        interceptors = null
    }
}

/**
 * Executes this pipeline
 */
@Suppress("NOTHING_TO_INLINE")
suspend inline fun <TContext : Any> Pipeline<Unit, TContext>.execute(context: TContext) = execute(context, Unit)

/**
 * Intercepts an untyped pipeline when the subject is of the given type
 */
inline fun <reified TSubject : Any, TContext : Any> Pipeline<*, TContext>.intercept(
    phase: PipelinePhase,
    noinline block: suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit
) {

    intercept(phase) interceptor@{ subject ->
        subject as? TSubject ?: return@interceptor
        @Suppress("UNCHECKED_CAST")
        val reinterpret = this as? PipelineContext<TSubject, TContext>
        reinterpret?.block(subject)
    }
}

/**
 * Represents an interceptor type which is a suspend extension function for context
 */
typealias PipelineInterceptor<TSubject, TContext> = suspend PipelineContext<TSubject, TContext>.(TSubject) -> Unit


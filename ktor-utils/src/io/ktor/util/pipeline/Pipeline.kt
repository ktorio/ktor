package io.ktor.util.pipeline

import io.ktor.util.*
import kotlin.coroutines.*
import kotlin.jvm.*

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
            createContext(context, subject, coroutineContext).proceed()

    internal fun createContext(
        context: TContext,
        subject: TSubject, coroutineContext: CoroutineContext
    ): PipelineContext<TSubject, TContext> =
        PipelineContext(context, interceptorsShared.sharedList(), subject, coroutineContext)

    private class PhaseContent<TSubject : Any, Call : Any>(
        val phase: PipelinePhase,
        val relation: PipelinePhaseRelation,
        private var interceptors: ArrayList<PipelineInterceptor<TSubject, Call>>
    ) {
        @Suppress("UNCHECKED_CAST")
        constructor(phase: PipelinePhase,
                    relation: PipelinePhaseRelation) : this(phase, relation, SharedArrayList as ArrayList<PipelineInterceptor<TSubject, Call>>) {
            check(SharedArrayList.isEmpty()) { "The shared empty array list has been modified" }
        }

        var shared: Boolean = true

        val isEmpty: Boolean get() = interceptors.isEmpty()
        val size: Int get() = interceptors.size

        fun addInterceptor(interceptor: PipelineInterceptor<TSubject, Call>) {
            if (shared) {
                copyInterceptors()
            }
            interceptors.add(interceptor)
        }

        fun addTo(destination: ArrayList<PipelineInterceptor<TSubject, Call>>) {
            val interceptors = interceptors
            destination.ensureCapacity(destination.size + interceptors.size)
            for (index in 0 until interceptors.size) {
                destination.add(interceptors[index])
            }
        }

        fun addTo(destination: PhaseContent<TSubject, Call>) {
            if (isEmpty) return
            if (destination.isEmpty) {
                destination.interceptors = sharedInterceptors()
                destination.shared = true
                return
            }

            if (destination.shared) {
                destination.copyInterceptors()
            }

            addTo(destination.interceptors)
        }

        fun sharedInterceptors(): ArrayList<PipelineInterceptor<TSubject, Call>> {
            shared = true
            return interceptors
        }

        fun copiedInterceptors(): ArrayList<PipelineInterceptor<TSubject, Call>> = ArrayList(interceptors)

        override fun toString(): String = "Phase `${phase.name}`, $size handlers"

        private fun copyInterceptors() {
            interceptors = copiedInterceptors()
            shared = false
        }

        companion object {
            val SharedArrayList = ArrayList<Any?>(0)
        }
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
        PhaseContent(it, PipelinePhaseRelation.Last)
    }

    internal fun phaseInterceptors(phase: PipelinePhase): List<PipelineInterceptor<TSubject, TContext>> =
        phases.first { it.phase == phase }.sharedInterceptors()

    private var interceptorsQuantity = 0

    private val interceptorsShared = InterceptorsListShared()

    /**
     * Phases of this pipeline
     */
    val items: List<PipelinePhase> get() = phases.map { it.phase }

    /**
     * Adds [phase] to the end of this pipeline
     */
    fun addPhase(phase: PipelinePhase) {
        if (phases.any { it.phase == phase }) return
        phases.add(PhaseContent(phase, PipelinePhaseRelation.Last))
    }

    /**
     * Inserts [phase] after the [reference] phase
     */
    fun insertPhaseAfter(reference: PipelinePhase, phase: PipelinePhase) {
        if (phases.any { it.phase == phase }) return
        val index = phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        phases.add(index + 1, PhaseContent(phase, PipelinePhaseRelation.After(reference)))
    }

    /**
     * Inserts [phase] before the [reference] phase
     */
    fun insertPhaseBefore(reference: PipelinePhase, phase: PipelinePhase) {
        if (phases.any { it.phase == phase }) return
        val index = phases.indexOfFirst { it.phase == reference }
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        phases.add(index, PhaseContent(phase, PipelinePhaseRelation.Before(reference)))
    }

    /**
     * @return `true` if there are no interceptors installed regardless number of phases
     */
    @InternalAPI
    val isEmpty: Boolean get() = interceptorsQuantity == 0

    /**
     * For tests only
     */
    internal fun interceptorsForTests(): List<PipelineInterceptor<TSubject, TContext>> {
        return interceptorsShared.getInterceptorsUnsafe() ?: cacheInterceptors()
    }

    private fun cacheInterceptors(): List<PipelineInterceptor<TSubject, TContext>> {
        val interceptorsQuantity = interceptorsQuantity
        if (interceptorsQuantity == 0) {
            interceptorsShared.notShared(emptyList())
            return emptyList()
        }

        val phases = phases
        if (interceptorsQuantity == 1) {
            for (phaseIndex in 0..phases.lastIndex) {
                val phaseContent = phases[phaseIndex]
                if (!phaseContent.isEmpty) {
                    val interceptors = phaseContent.sharedInterceptors()
                    interceptorsShared.fromPhase(phaseContent)
                    return interceptors
                }
            }
        }

        val destination = ArrayList<PipelineInterceptor<TSubject, TContext>>(interceptorsQuantity)
        for (phaseIndex in 0..phases.lastIndex) {
            phases[phaseIndex].addTo(destination)
        }

        interceptorsShared.notShared(destination)
        return destination
    }

    /**
     * Adds [block] to the [phase] of this pipeline
     */
    open fun intercept(phase: PipelinePhase, block: PipelineInterceptor<TSubject, TContext>) {
        val phaseContent = phases.firstOrNull { it.phase == phase }
                ?: throw InvalidPhaseException("Phase $phase was not registered for this pipeline")

        if (interceptorsShared.tryAddToPhase(phase, block)) {
            interceptorsQuantity++
            return
        }

        phaseContent.addInterceptor(block)
        interceptorsQuantity++
        interceptorsShared.reset()
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
        if (fastPathMerge(from)) {
            return
        }

        if (interceptorsQuantity == 0) {
            interceptorsShared.fromAnotherPipeline(from)
        } else {
            interceptorsShared.reset()
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

            // addAll triggers allocations even for empty collections
            fromContent.addTo(phaseContent)
            interceptorsQuantity += fromContent.size
        }
    }

    private fun <E> ArrayList<E>.addAllAF(from: ArrayList<E>) {
        ensureCapacity(size + from.size)
        for (index in 0 until from.size) {
            add(from[index])
        }
    }

    private fun fastPathMerge(from: Pipeline<TSubject, TContext>): Boolean {
        if (from.phases.isEmpty())
            return true

        if (phases.isEmpty()) {
            val fromPhases = from.phases
            @Suppress("LoopToCallChain")
            for (index in 0..fromPhases.lastIndex) {
                val fromContent = fromPhases[index]
                phases.add(PhaseContent(fromContent.phase, fromContent.relation, fromContent.sharedInterceptors()))
            }
            interceptorsQuantity += from.interceptorsQuantity
            interceptorsShared.fromAnotherPipeline(from)
            return true
        }
        return false
    }

    private inner class InterceptorsListShared {
        @Volatile
        private var interceptors: List<PipelineInterceptor<TSubject, TContext>>? = null

        /**
         * share between pipelines/contexts
         */
        private var interceptorsListShared = false

        /**
         * interceptors list is shared with pipeline phase content
         */
        private var interceptorsListSharedPhase: PipelinePhase? = null

        internal fun getInterceptorsUnsafe() = interceptors

        fun sharedList(): List<PipelineInterceptor<TSubject, TContext>> {
            if (interceptors == null) {
                cacheInterceptors()
            }
            interceptorsListShared = true
            return interceptors!!
        }

        fun reset() {
            interceptors = null
            interceptorsListShared = false
            interceptorsListSharedPhase = null
        }

        fun notShared(list: List<PipelineInterceptor<TSubject, TContext>>) {
            interceptors = list
            interceptorsListShared = false
            interceptorsListSharedPhase = null
        }

        fun fromPhase(phaseContent: PhaseContent<TSubject, TContext>) {
            this.interceptors = phaseContent.sharedInterceptors()
            this.interceptorsListShared = false
            this.interceptorsListSharedPhase = phaseContent.phase
        }

        fun fromAnotherPipeline(pipeline: Pipeline<TSubject, TContext>) {
            this.interceptors = pipeline.interceptorsShared.sharedList()
            this.interceptorsListShared = true
            this.interceptorsListSharedPhase = null
        }

        fun tryAddToPhase(phase: PipelinePhase, block: PipelineInterceptor<TSubject, TContext>): Boolean {
            if (phases.isEmpty()) return false
            if (interceptors == null) return false

            if (!interceptorsListShared) {
                if (interceptorsListSharedPhase == phase) {
                    (interceptors as? MutableList)?.let {
                        it.add(block)
                        return true
                    }
                }
                if (phase == phases.last().phase && interceptors is MutableList) {
                    phases.last().addInterceptor(block)
                    (interceptors as MutableList).let {
                        it.add(block)
                        return true
                    }
                }
            }

            return false
        }
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


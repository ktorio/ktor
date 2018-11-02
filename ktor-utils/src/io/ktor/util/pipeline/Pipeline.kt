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
        createContext(context, subject).execute(subject)

    internal fun createContext(
        context: TContext,
        subject: TSubject
    ): PipelineExecutor<TSubject> =
        pipelineExecutorFor(context, sharedInterceptorsList(), subject)

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

//    private val phases = phases.mapTo(ArrayList<PhaseContent<TSubject, TContext>>(phases.size)) {
//        PhaseContent(it, PipelinePhaseRelation.Last)
//    }

    // array of phases or phase contents
    private val phasesRaw: ArrayList<Any> = phases.mapTo(ArrayList<Any>(phases.size + 1)) {
        it
    }

    private fun findPhase(phase: PipelinePhase): PhaseContent<TSubject, TContext>? {
        val phasesList = phasesRaw
        for (index in 0 until phasesList.size) {
            val e = phasesList[index]
            if (e === phase) {
                val content = PhaseContent<TSubject, TContext>(phase, PipelinePhaseRelation.Last)
                phasesList[index] = content
                return content
            } else if (e is PhaseContent<*, *> && e.phase === phase) {
                @Suppress("UNCHECKED_CAST")
                return e as PhaseContent<TSubject, TContext>
            }
        }

        return null
    }

    private fun findPhaseIndex(phase: PipelinePhase): Int {
        val phasesList = phasesRaw
        for (index in 0 until phasesList.size) {
            val e = phasesList[index]
            if (e === phase) {
                return index
            } else if (e is PhaseContent<*, *> && e.phase === phase) {
                return index
            }
        }

        return -1
    }

    private fun hasPhase(phase: PipelinePhase): Boolean {
        val phasesList = phasesRaw
        for (index in 0 until phasesList.size) {
            val e = phasesList[index]
            if (e === phase) {
                return true
            } else if (e is PhaseContent<*, *> && e.phase === phase) {
                return true
            }
        }

        return false
    }

    internal fun phaseInterceptors(phase: PipelinePhase): List<PipelineInterceptor<TSubject, TContext>> =
        findPhase(phase)?.sharedInterceptors() ?: emptyList()

    private var interceptorsQuantity = 0

    /**
     * Phases of this pipeline
     */
    val items: List<PipelinePhase>
        get() = phasesRaw.map {
            it as? PipelinePhase ?: (it as? PhaseContent<*, *>)?.phase!!
        }

    /**
     * Adds [phase] to the end of this pipeline
     */
    fun addPhase(phase: PipelinePhase) {
        if (hasPhase(phase)) return
        phasesRaw.add(phase)
    }

    /**
     * Inserts [phase] after the [reference] phase
     */
    fun insertPhaseAfter(reference: PipelinePhase, phase: PipelinePhase) {
        if (hasPhase(phase)) return
        val index = findPhaseIndex(reference)
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        phasesRaw.add(index + 1, PhaseContent<TSubject, TContext>(phase, PipelinePhaseRelation.After(reference)))
    }

    /**
     * Inserts [phase] before the [reference] phase
     */
    fun insertPhaseBefore(reference: PipelinePhase, phase: PipelinePhase) {
        if (hasPhase(phase)) return
        val index = findPhaseIndex(reference)
        if (index == -1)
            throw InvalidPhaseException("Phase $reference was not registered for this pipeline")
        phasesRaw.add(index, PhaseContent<TSubject, TContext>(phase, PipelinePhaseRelation.Before(reference)))
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
        return interceptors ?: cacheInterceptors()
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

        val destination = ArrayList<PipelineInterceptor<TSubject, TContext>>(interceptorsQuantity)
        for (phaseIndex in 0..phases.lastIndex) {
            @Suppress("UNCHECKED_CAST")
            val phase =
                phases[phaseIndex] as? PhaseContent<TSubject, TContext> ?: continue
            phase.addTo(destination)
        }

        notSharedInterceptorsList(destination)
        return destination
    }

    /**
     * Adds [block] to the [phase] of this pipeline
     */
    fun intercept(phase: PipelinePhase, block: PipelineInterceptor<TSubject, TContext>) {
        val phaseContent = findPhase(phase)
            ?: throw InvalidPhaseException("Phase $phase was not registered for this pipeline")

        if (tryAddToPhaseFastpath(phase, block)) {
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
    open fun afterIntercepted() {
    }

    /**
     * Merges another pipeline into this pipeline, maintaining relative phases order
     */
    fun merge(from: Pipeline<TSubject, TContext>) {
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

    private fun <E> ArrayList<E>.addAllAF(from: ArrayList<E>) {
        ensureCapacity(size + from.size)
        for (index in 0 until from.size) {
            add(from[index])
        }
    }

    private fun fastPathMerge(from: Pipeline<TSubject, TContext>): Boolean {
        if (from.phasesRaw.isEmpty())
            return true

        if (phasesRaw.isEmpty()) {
            val fromPhases = from.phasesRaw
            @Suppress("LoopToCallChain")
            for (index in 0..fromPhases.lastIndex) {
                val fromPhaseOrContent = fromPhases[index]
                if (fromPhaseOrContent is PipelinePhase) {
                    phasesRaw.add(fromPhaseOrContent)
                } else if (fromPhaseOrContent is PhaseContent<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    fromPhaseOrContent as PhaseContent<TSubject, TContext>

                    phasesRaw.add(
                        PhaseContent(
                            fromPhaseOrContent.phase,
                            fromPhaseOrContent.relation,
                            fromPhaseOrContent.sharedInterceptors()
                        )
                    )
                }
            }
            interceptorsQuantity += from.interceptorsQuantity
            setInterceptorsListFromAnotherPipeline(from)
            return true
        }

        return false
    }

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
        this.interceptors = phaseContent.sharedInterceptors()
        this.interceptorsListShared = false
        this.interceptorsListSharedPhase = phaseContent.phase
    }

    private fun setInterceptorsListFromAnotherPipeline(pipeline: Pipeline<TSubject, TContext>) {
        this.interceptors = pipeline.sharedInterceptorsList()
        this.interceptorsListShared = true
        this.interceptorsListSharedPhase = null
    }

    private fun tryAddToPhaseFastpath(phase: PipelinePhase, block: PipelineInterceptor<TSubject, TContext>): Boolean {
        if (phasesRaw.isEmpty()) return false
        if (interceptors == null) return false

        if (!interceptorsListShared) {
            if (interceptorsListSharedPhase == phase) {
                (interceptors as? MutableList)?.let {
                    it.add(block)
                    return true
                }
            }
            if ((phase == phasesRaw.last() || findPhaseIndex(phase) == phasesRaw.lastIndex) && interceptors is MutableList) {
                findPhase(phase)!!.addInterceptor(block)
                (interceptors as MutableList).let {
                    it.add(block)
                    return true
                }
            }
        }

        return false
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

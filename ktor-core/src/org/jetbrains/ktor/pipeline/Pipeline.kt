package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.util.*

open class Pipeline<TSubject : Any>(vararg phase: PipelinePhase) {
    /**
     * Provides common place to store pipeline attributes
     */
    val attributes = Attributes()

    val phases = PipelinePhases<TSubject>(*phase)

    constructor(phase: PipelinePhase, interceptors: List<PipelineInterceptor<TSubject>>) : this(phase) {
        interceptors.forEach { phases.intercept(phase, it) }
    }

    open fun intercept(phase: PipelinePhase, block: PipelineInterceptor<TSubject>) {
        phases.intercept(phase, block)
    }

    suspend fun execute(subject: TSubject) {
        val interceptors = phases.interceptors()
        if (interceptors.isEmpty())
            return
        val interceptor = interceptors[0]
        val context = Context(interceptors, 0, subject)
        interceptor.invoke(context, subject)
    }

    class Context<TSubject : Any>(private val interceptors: List<PipelineInterceptor<TSubject>>, val index: Int, override val subject: TSubject) : PipelineContext<TSubject> {
        suspend override fun proceed() {
            if (interceptors.lastIndex == index)
                return
            val context = Context(interceptors, index + 1, subject)
            interceptors[index + 1].invoke(context, subject)
        }
    }

}

typealias PipelineInterceptor<TSubject> = suspend PipelineContext<TSubject>.(TSubject) -> Unit


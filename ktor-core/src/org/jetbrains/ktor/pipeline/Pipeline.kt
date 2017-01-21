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
        val context = object : PipelineContext<TSubject> {
            override val subject get() = subject

            suspend override fun proceed() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            suspend override fun <T : Any> fork(value: T, pipeline: Pipeline<T>) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        }
        for (interceptor in phases.interceptors()) {
            interceptor.invoke(context, subject)
        }
    }

}

typealias PipelineInterceptor<TSubject> = suspend PipelineContext<TSubject>.(TSubject) -> Unit
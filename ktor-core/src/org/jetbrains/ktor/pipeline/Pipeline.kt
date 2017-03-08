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

    suspend fun execute(subject: TSubject): TSubject = PipelineContext(phases.interceptors(), subject).proceed()
}

typealias PipelineInterceptor<TSubject> = suspend PipelineContext<TSubject>.(TSubject) -> Unit


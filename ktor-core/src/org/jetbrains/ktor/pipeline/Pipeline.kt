package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.util.*

open class Pipeline<TSubject : Any>(vararg phase: PipelinePhase) {
    /**
     * Provides common place to store pipeline attributes
     */
    val attributes = Attributes()

    val interceptors: List<PipelineContext<TSubject>.(TSubject) -> Unit> get() = phases.interceptors()

    val phases = PipelinePhases<TSubject>(*phase)

    constructor(phase: PipelinePhase, interceptors: List<PipelineContext<TSubject>.(TSubject) -> Unit>) : this(phase) {
        interceptors.forEach { phases.intercept(phase, it) }
    }

    fun intercept(phase: PipelinePhase, block: PipelineContext<TSubject>.(TSubject) -> Unit) {
        phases.intercept(phase, block)
    }

    fun merge(from: Pipeline<TSubject>) {
        phases.merge(from.phases)
    }
}


package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*
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

    suspend fun execute(call: ApplicationCall, subject: TSubject): TSubject = PipelineContext(call, phases.interceptors(), subject).proceed()
}

suspend fun Pipeline<Unit>.execute(call: ApplicationCall) = execute(call, Unit)

typealias PipelineInterceptor<TSubject> = suspend PipelineContext<TSubject>.(TSubject) -> Unit


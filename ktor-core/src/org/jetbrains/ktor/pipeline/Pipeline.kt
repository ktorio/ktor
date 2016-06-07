package org.jetbrains.ktor.pipeline

open class Pipeline<TSubject : Any>(vararg phase: PipelinePhase) {
    val interceptors: List<PipelineContext<TSubject>.(TSubject) -> Unit> get() = phases.interceptors()

    val phases = PipelinePhases<TSubject>(*phase)

    constructor(phase: PipelinePhase, interceptors: List<PipelineContext<TSubject>.(TSubject) -> Unit>) : this(phase) {
        interceptors.forEach { phases.intercept(phase, it) }
    }

    fun intercept(phase: PipelinePhase, block: PipelineContext<TSubject>.(TSubject) -> Unit) {
        phases.intercept(phase, block)
    }
}

open class PipelineControlFlow : Throwable() {
    @Suppress("unused", "VIRTUAL_MEMBER_HIDDEN") // implicit override
    fun fillInStackTrace(): Throwable? {
        return null
    }
}

class PipelineCompleted : PipelineControlFlow()
class PipelinePaused : PipelineControlFlow()
class PipelineContinue : PipelineControlFlow()
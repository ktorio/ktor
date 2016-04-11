package org.jetbrains.ktor.pipeline

class Pipeline<TSubject : Any>() {
    val interceptors = mutableListOf<PipelineContext<TSubject>.(TSubject) -> Unit>()

    constructor(interceptors: List<PipelineContext<TSubject>.(TSubject) -> Unit>) : this() {
        this.interceptors.addAll(interceptors)
    }

    fun intercept(block: PipelineContext<TSubject>.(TSubject) -> Unit) {
        interceptors.add(block)
    }

    fun intercept(index: Int, block: PipelineContext<TSubject>.(TSubject) -> Unit) {
        interceptors.add(index, block)
    }
}

open class PipelineControlFlow : Throwable() {
    @Suppress("unused") // implicit override
    fun fillInStackTrace(): Throwable? {
        return null
    }
}

class PipelineCompleted : PipelineControlFlow()
class PipelinePaused : PipelineControlFlow()
class PipelineContinue : PipelineControlFlow()
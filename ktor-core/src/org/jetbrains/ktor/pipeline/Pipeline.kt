package org.jetbrains.ktor.pipeline

open class Pipeline<TSubject : Any>() {
    private val _interceptors = mutableListOf<PipelineContext<TSubject>.(TSubject) -> Unit>()

    val interceptors : List<PipelineContext<TSubject>.(TSubject) -> Unit> get() = _interceptors

    constructor(interceptors: List<PipelineContext<TSubject>.(TSubject) -> Unit>) : this() {
        _interceptors.addAll(interceptors)
    }

    fun intercept(block: PipelineContext<TSubject>.(TSubject) -> Unit) {
        _interceptors.add(block)
    }

    fun intercept(index: Int, block: PipelineContext<TSubject>.(TSubject) -> Unit) {
        if (index >= 0) {
            _interceptors.add(index, block)
        } else {
            _interceptors.add(_interceptors.size + index, block)
        }
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
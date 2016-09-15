package org.jetbrains.ktor.pipeline

interface PipelineContext<out TSubject : Any> {
    val subject: TSubject
    val exception: Throwable?

    fun onSuccess(body: PipelineContext<TSubject>.(Any) -> Unit)
    fun onFail(body: PipelineContext<TSubject>.(Any) -> Unit)

    fun onFinish(body: PipelineContext<TSubject>.(Any) -> Unit) {
        onSuccess(body)
        onFail(body)
    }

    fun repeat()

    fun pause(): Nothing
    fun proceed(): Nothing
    fun fail(exception: Throwable): Nothing
    fun finish(): Nothing
    fun finishAll(): Nothing
    fun <T : Any> fork(value: T, pipeline: Pipeline<T>): Nothing
}
package org.jetbrains.ktor.pipeline

interface PipelineContext<out TSubject : Any> {

    val subject: TSubject

    fun onSuccess(body: () -> Unit)
    fun onFail(body: (Throwable) -> Unit)

    fun onFinish(body: () -> Unit) {
        onSuccess(body)
        onFail { body() }
    }

    fun pause(): Nothing
    fun proceed(): Nothing
    fun fail(exception: Throwable): Nothing
    fun finish(): Nothing
    fun finishAll(): Nothing
    fun <T : Any> fork(value: T, pipeline: Pipeline<T>): Nothing
}
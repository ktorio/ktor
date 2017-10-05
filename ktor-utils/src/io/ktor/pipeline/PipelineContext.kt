package io.ktor.pipeline

@ContextDsl
class PipelineContext<TSubject : Any, out TContext : Any>(
        val context: TContext,
        private val interceptors: List<PipelineInterceptor<TSubject, TContext>>,
        subject: TSubject
) {
    var subject: TSubject = subject
        private set

    private var index = 0

    fun finish() {
        index = -1
    }

    suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    suspend fun proceed(): TSubject {
        while (index >= 0) {
            if (interceptors.size == index) {
                index = -1 // finished
                return subject
            }
            val executeInterceptor = interceptors[index]
            index++
            executeInterceptor.invoke(this, subject)
        }
        return subject
    }
}

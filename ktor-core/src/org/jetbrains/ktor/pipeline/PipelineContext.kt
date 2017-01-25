package org.jetbrains.ktor.pipeline

class PipelineContext<out TSubject : Any>(private val interceptors: List<PipelineInterceptor<TSubject>>, val subject: TSubject) {
    private var index = 0

    fun finish() {
        index = -1
    }

    suspend fun proceed() {
        while (index >= 0) {
            if (interceptors.size == index) {
                index = -1 // finished
                return
            }
            val executeInterceptor = interceptors[index]
            index++
            executeInterceptor.invoke(this, subject)
        }
    }
}

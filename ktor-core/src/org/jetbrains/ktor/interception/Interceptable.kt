package org.jetbrains.ktor.interception

@Deprecated("")
class Interceptable0<TResult>(function: () -> TResult) {
    private val interceptors = arrayListOf(function)

    @Deprecated("")
    fun intercept(handler: (next: () -> TResult) -> TResult) {
        val index = interceptors.lastIndex
        val nextHandler: () -> TResult = { interceptors[index + 1]() }
        val function = interceptors[index]
        interceptors[index] = { handler(nextHandler) }
        interceptors.add(function)
    }

    @Deprecated("")
    fun execute(): TResult = interceptors[0]()
}


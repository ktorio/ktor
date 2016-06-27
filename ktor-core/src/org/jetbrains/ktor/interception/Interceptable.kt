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

@Deprecated("")
class Interceptable1<TParam0, TResult>(function: (TParam0) -> TResult) {
    private val interceptors = arrayListOf(function)

    @Deprecated("")
    fun intercept(handler: (param: TParam0, next: (TParam0) -> TResult) -> TResult) {
        val index = interceptors.lastIndex
        val nextHandler: (TParam0) -> TResult = { interceptors[index + 1](it) }
        val function = interceptors[index]
        interceptors[index] = { handler(it, nextHandler) }
        interceptors.add(function)
    }

    @Deprecated("")
    fun execute(param: TParam0): TResult = interceptors[0](param)
}

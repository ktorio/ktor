package org.jetbrains.ktor.interception

class Interceptable0<TResult>(function: () -> TResult) {
    private val interceptors = arrayListOf<() -> TResult>(function)

    fun intercept(handler: (next: () -> TResult) -> TResult) {
        val index = interceptors.lastIndex
        val nextHandler: () -> TResult = { interceptors[index + 1]() }
        val function = interceptors[index]
        interceptors[index] = { handler(nextHandler) }
        interceptors.add(function)
    }

    fun execute(): TResult = interceptors[0]()
}

class Interceptable1<TParam0, TResult>(function: (TParam0) -> TResult) {
    private val interceptors = arrayListOf<(TParam0) -> TResult>(function)

    fun intercept(handler: (param: TParam0, next: (TParam0) -> TResult) -> TResult) {
        val index = interceptors.lastIndex
        val nextHandler: (TParam0) -> TResult = { interceptors[index + 1](it) }
        val function = interceptors[index]
        interceptors[index] = { handler(it, nextHandler) }
        interceptors.add(function)
    }

    fun execute(param: TParam0): TResult = interceptors[0](param)
}

class Interceptable2<TParam0, TParam1, TResult>(val function: (TParam0, TParam1) -> TResult) {
    private val interceptors = arrayListOf<(TParam0, TParam1) -> TResult>(function)

    fun intercept(handler: (param1: TParam0, param2: TParam1, next: (TParam0, TParam1) -> TResult) -> TResult) {
        val index = interceptors.lastIndex
        val nextHandler: (TParam0, TParam1) -> TResult = { p1, p2 -> interceptors[index + 1](p1, p2) }
        val function = interceptors[index]
        interceptors[index] = { p1, p2 -> handler(p1, p2, nextHandler) }
        interceptors.add(function)
    }

    fun execute(param1: TParam0, param2: TParam1): TResult = interceptors[0](param1, param2)
}

public class InterceptableChain0<TResult> {
    private val interceptors = arrayListOf<(() -> TResult) -> TResult>()

    fun intercept(handler: (() -> TResult) -> TResult) {
        interceptors.add(handler)
    }

    fun execute(next: () -> TResult): TResult = invokeAt(next, 0)

    private fun invokeAt(last: () -> TResult, index: Int): TResult = when {
        index < interceptors.size -> interceptors[index]({ invokeAt(last, index + 1) })
        else -> last()
    }

}


public class InterceptableChain1<TParam0, TResult> {
    private val interceptors = arrayListOf<(TParam0, (TParam0) -> TResult) -> TResult>()

    public fun intercept(handler: (param: TParam0, next: (TParam0) -> TResult) -> TResult) {
        interceptors.add(handler)
    }

    fun invokeAt(param: TParam0, last: (TParam0) -> TResult, index: Int): TResult = when {
        index < interceptors.size -> interceptors[index](param, { param -> invokeAt(param, last, index + 1) })
        else -> last(param)
    }

    public fun execute(param: TParam0, last: (TParam0) -> TResult): TResult = invokeAt(param, last, 0)
}

public class InterceptableChain2<TParam0, TParam1, TResult> {
    private val interceptors = arrayListOf<(TParam0, TParam1, (TParam0, TParam1) -> TResult) -> TResult>()

    public fun intercept(handler: (TParam0, TParam1, (TParam0, TParam1) -> TResult) -> TResult) {
        interceptors.add(handler)
    }

    fun invokeAt(param1: TParam0, param2: TParam1, last: (TParam0, TParam1) -> TResult, index: Int): TResult = when {
        index < interceptors.size -> interceptors[index](param1, param2, { param1, param2 -> invokeAt(param1, param2, last, index + 1) })
        else -> last(param1, param2)
    }

    public fun execute(param1: TParam0, param2: TParam1, last: (TParam0, TParam1) -> TResult): TResult = invokeAt(param1, param2, last, 0)
}
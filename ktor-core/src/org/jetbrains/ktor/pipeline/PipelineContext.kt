package org.jetbrains.ktor.pipeline

import kotlin.reflect.*
import kotlin.reflect.jvm.*

interface PipelineContext<out TSubject : Any> {
    val subject: TSubject

    suspend fun proceed()
    suspend fun <T : Any> fork(value: T, pipeline: Pipeline<T>)
}

class X {
    fun x() {}
    suspend fun y() {}
}

fun main(args: Array<String>) {
    val fn: KFunction1<X, Unit> = X::x
}
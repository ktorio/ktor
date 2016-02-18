package org.jetbrains.ktor.interception

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*

interface InterceptApplicationCall<C : ApplicationCall> {
    fun intercept(interceptor: PipelineContext<C>.(C) -> Unit)
}

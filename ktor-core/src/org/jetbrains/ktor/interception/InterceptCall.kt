package org.jetbrains.ktor.interception

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*

interface InterceptApplicationCall {
    fun intercept(interceptor: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit)
}

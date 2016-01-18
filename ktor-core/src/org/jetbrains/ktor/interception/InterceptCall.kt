package org.jetbrains.ktor.interception

import org.jetbrains.ktor.application.*

interface InterceptApplicationCall<C : ApplicationCall> {
    fun intercept(interceptor: C.(C.() -> ApplicationCallResult) -> ApplicationCallResult)
}

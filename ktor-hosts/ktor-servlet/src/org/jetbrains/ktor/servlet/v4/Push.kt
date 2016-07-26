package org.jetbrains.ktor.servlet.v4

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import javax.servlet.http.*

@Suppress("unused")
fun doPush(request: HttpServletRequest, call: ApplicationCall, block: ResponsePushBuilder.() -> Unit, next: () -> Unit) {
    request.pushBuilder.apply {
        if (this is HttpServletRequest.NoOpPushBuilder) {
            return next()
        }

        val builder = DefaultResponsePushBuilder(call)
        builder.block()

        this.method(builder.method.value)
        this.path(builder.url.encodedPath)
        this.queryString(builder.url.build().substringAfter('?', ""))

        push()
    }
}

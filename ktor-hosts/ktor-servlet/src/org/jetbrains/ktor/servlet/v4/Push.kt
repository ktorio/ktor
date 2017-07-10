package org.jetbrains.ktor.servlet.v4

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.response.*
import javax.servlet.http.*

@Suppress("unused")
fun doPush(request: HttpServletRequest, call: ApplicationCall, block: ResponsePushBuilder.() -> Unit, next: () -> Unit) {
    request.newPushBuilder()?.apply {
        val builder = DefaultResponsePushBuilder(call)
        builder.block()

        this.method(builder.method.value)
        this.path(builder.url.encodedPath)

        val query = builder.url.build().substringAfter('?', "").takeIf { it.isNotEmpty() }
        if (query != null) {
            queryString(query)
        }

        push()
    } ?: next()
}

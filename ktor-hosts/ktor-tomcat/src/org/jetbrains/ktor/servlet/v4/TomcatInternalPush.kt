package org.jetbrains.ktor.servlet.v4


import org.apache.catalina.connector.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import javax.servlet.http.*

@Suppress("unused")
fun doPushInternal(request: HttpServletRequest, call: ApplicationCall, block: ResponsePushBuilder.() -> Unit, next: () -> Unit) {
    val pb = if (request is RequestFacade) {
        request.newPushBuilder()
    } else if (request is Request) {
        request.newPushBuilder()
    } else null

    pb?.apply {
        val builder = DefaultResponsePushBuilder(call)
        builder.block()

        this.method(builder.method.value)
        this.path(builder.url.encodedPath)
        this.queryString(builder.url.build().substringAfter('?', ""))

        push()
    } ?: next()
}

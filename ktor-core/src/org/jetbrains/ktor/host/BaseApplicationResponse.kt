package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.channels.*
import java.nio.file.*
import java.time.*

abstract class BaseApplicationResponse() : ApplicationResponse {
    protected abstract val stream: Interceptable1<OutputStream.() -> Unit, Unit>
    protected abstract val status: Interceptable1<HttpStatusCode, Unit>

    override val cookies = ResponseCookies(this)

    override fun status(value: HttpStatusCode) = status.execute(value)
    override fun interceptStatus(handler: (HttpStatusCode, (HttpStatusCode) -> Unit) -> Unit) = status.intercept(handler)

    override fun stream(body: OutputStream.() -> Unit): Unit = stream.execute(body)
    override fun interceptStream(handler: (OutputStream.() -> Unit, (OutputStream.() -> Unit) -> Unit) -> Unit) = stream.intercept(handler)
}
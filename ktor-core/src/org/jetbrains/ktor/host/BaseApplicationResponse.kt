package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*
import java.io.*

abstract class BaseApplicationResponse() : ApplicationResponse {
    protected abstract val channel: Interceptable0<AsyncWriteChannel>
    protected abstract val stream: Interceptable1<OutputStream.() -> Unit, Unit>

    override val cookies = ResponseCookies(this)

    override fun stream(body: OutputStream.() -> Unit): Unit = stream.execute(body)
    override fun interceptStream(handler: (OutputStream.() -> Unit, (OutputStream.() -> Unit) -> Unit) -> Unit) = stream.intercept(handler)

    override fun channel() = channel.execute()
    override fun interceptChannel(handler: (() -> AsyncWriteChannel) -> AsyncWriteChannel) = channel.intercept(handler)
}
package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*
import java.io.*

abstract class BaseApplicationResponse() : ApplicationResponse {
    protected abstract val channel: Interceptable0<AsyncWriteChannel>

    override val cookies = ResponseCookies(this)

    override fun channel() = channel.execute()
    override fun interceptChannel(handler: (() -> AsyncWriteChannel) -> AsyncWriteChannel) = channel.intercept(handler)
}
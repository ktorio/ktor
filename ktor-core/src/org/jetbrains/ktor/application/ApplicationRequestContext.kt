package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*

public interface ApplicationRequestContext {
    public val application: Application
    public val request: ApplicationRequest
    public val response: ApplicationResponse

    public val close: Interceptable0<Unit>
}

public fun ApplicationRequestContext.close(): Unit = close.call()

package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import javax.servlet.*
import javax.servlet.http.*

public class ServletApplicationRequestContext(override val application: Application,
                                              private val servletRequest: HttpServletRequest,
                                              private val servletResponse: HttpServletResponse) : ApplicationRequestContext {
    override val request = ServletApplicationRequest(servletRequest)
    override val response = ServletApplicationResponse(servletResponse)

    private var asyncContext: AsyncContext? = null

    fun continueAsync(asyncContext: AsyncContext) {
        this.asyncContext = asyncContext
    }

    override val close = Interceptable0 {
        servletResponse.flushBuffer()
        if (asyncContext != null) {
            asyncContext?.complete()
        }
    }
}
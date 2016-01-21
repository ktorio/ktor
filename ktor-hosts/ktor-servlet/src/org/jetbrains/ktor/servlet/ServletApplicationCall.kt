package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.util.*
import javax.servlet.*
import javax.servlet.http.*

public class ServletApplicationCall(override val application: Application,
                                    private val servletRequest: HttpServletRequest,
                                    private val servletResponse: HttpServletResponse) : ApplicationCall {
    override val attributes = Attributes()
    override val request : ApplicationRequest = ServletApplicationRequest(servletRequest)
    override val response : ApplicationResponse = ServletApplicationResponse(this, servletRequest, servletResponse)

    private var asyncContext: AsyncContext? = null

    fun continueAsync(asyncContext: AsyncContext) {
        // TODO: assert that continueAsync was not yet called
        this.asyncContext = asyncContext
    }

    val asyncStarted: Boolean
        get() = asyncContext != null

    override val close = Interceptable0 {
        servletResponse.flushBuffer()
        if (asyncContext != null) {
            asyncContext?.complete()
        }
    }
}
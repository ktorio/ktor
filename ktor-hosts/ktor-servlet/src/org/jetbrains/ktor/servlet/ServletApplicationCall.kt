package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
import javax.servlet.*
import javax.servlet.http.*

class ServletApplicationCall(application: Application,
                                    private val servletRequest: HttpServletRequest,
                                    private val servletResponse: HttpServletResponse) : BaseApplicationCall(application) {

    override val attributes = Attributes()
    override val request : ApplicationRequest = ServletApplicationRequest(servletRequest)
    override val response : ApplicationResponse = ServletApplicationResponse(this, servletResponse)
    override val parameters: ValuesMap get() = request.parameters

    @Volatile
    private var asyncContext: AsyncContext? = null

    fun continueAsync(asyncContext: AsyncContext) {
        require(this.asyncContext == null || this.asyncContext == asyncContext) { "You can't reassign asyncContext" }
        this.asyncContext = asyncContext
    }

    val asyncStarted: Boolean
        get() = asyncContext != null

    @Volatile
    var completed: Boolean = false
    override val close = Interceptable0 {
        completed = true
        if (asyncContext != null) {
            asyncContext?.complete()
        }
    }

    internal fun startAsync(): AsyncContext {
        val asyncContext = servletRequest.startAsync(servletRequest, servletResponse)
        // asyncContext.timeout = ?
        continueAsync(asyncContext)

        return asyncContext
    }

}
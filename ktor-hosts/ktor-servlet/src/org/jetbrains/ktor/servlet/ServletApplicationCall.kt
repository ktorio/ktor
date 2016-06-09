package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*
import javax.servlet.*
import javax.servlet.http.*

class ServletApplicationCall(application: Application,
                             private val servletRequest: HttpServletRequest,
                             private val servletResponse: HttpServletResponse,
                             executor: Executor,
                             val onAsyncStartedUnderLock: () -> Unit) : BaseApplicationCall(application, executor) {

    override val request: ApplicationRequest = ServletApplicationRequest(this, servletRequest)
    override val response: ApplicationResponse = ServletApplicationResponse(this, servletResponse)
    override val parameters: ValuesMap get() = request.parameters

    @Volatile
    private var asyncContext: AsyncContext? = null

    val asyncStarted: Boolean
        get() = asyncContext != null

    @Volatile
    var completed: Boolean = false

    @Synchronized
    override fun close() {
        if (!completed) {
            completed = true
            asyncContext?.complete()
        }
    }

    @Synchronized
    fun ensureAsync() {
        if (!asyncStarted) {
            startAsync()
        }
    }

    private fun startAsync() {
        require(this.asyncContext == null) { "You can't reassign asyncContext" }

        asyncContext = servletRequest.startAsync(servletRequest, servletResponse)
        // asyncContext.timeout = ?

        onAsyncStartedUnderLock()
    }

}
package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*

/**
 * Represents configured and running web application, capable of handling requests
 */
public open class Application(val config: ApplicationConfig) : InterceptableWithContext<ApplicationRequestContext> {
    private val handler: Interceptable1<ApplicationRequestContext, ApplicationRequestStatus> = Interceptable1 {
        ApplicationRequestStatus.Unhandled
    }

    /**
     * Installs interceptor into the current Application handling chain
     */
    override fun intercept(interceptor: ApplicationRequestContext.(ApplicationRequestContext.() -> ApplicationRequestStatus) -> ApplicationRequestStatus) {
        handler.intercept(interceptor)
    }

    /**
     * Handles HTTP request coming from the host using interceptors
     */
    public fun handle(context: ApplicationRequestContext): ApplicationRequestStatus {
        val result = handler.call(context)
        context.logResult(result)
        return result
    }

    private fun ApplicationRequestContext.logResult(result: ApplicationRequestStatus) {
        when (result) {
            ApplicationRequestStatus.Handled -> {
                val status = response.status()
                when (status) {
                    HttpStatusCode.Found -> config.log.info("$status: ${request.requestLine} -> ${response.headers[HttpHeaders.Location]}")
                    else -> config.log.info("$status: ${request.requestLine}")
                }
            }
            ApplicationRequestStatus.Unhandled -> config.log.info("<Unhandled>: ${request.requestLine}")
            ApplicationRequestStatus.Asynchronous -> config.log.info("<Async>: ${request.requestLine}")
        }
    }

    /**
     * Called by host when Application is terminated
     */
    public open fun dispose() {
    }
}

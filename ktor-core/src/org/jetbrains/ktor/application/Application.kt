package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*

/**
 * Represents configured and running web application, capable of handling requests
 */
public open class Application(val config: ApplicationConfig) : InterceptableWithContext<ApplicationCall> {
    private val handler: Interceptable1<ApplicationCall, ApplicationCallResult> = Interceptable1 {
        ApplicationCallResult.Unhandled
    }

    /**
     * Installs interceptor into the current Application handling chain
     */
    override fun intercept(interceptor: ApplicationCall.(ApplicationCall.() -> ApplicationCallResult) -> ApplicationCallResult) {
        handler.intercept(interceptor)
    }

    /**
     * Handles HTTP request coming from the host using interceptors
     */
    public fun handle(context: ApplicationCall): ApplicationCallResult {
        val result = handler.call(context)
        context.logResult(result)
        return result
    }

    private fun ApplicationCall.logResult(result: ApplicationCallResult) {
        when (result) {
            ApplicationCallResult.Handled -> {
                val status = response.status()
                when (status) {
                    HttpStatusCode.Found -> config.log.info("$status: ${request.requestLine} -> ${response.headers[HttpHeaders.Location]}")
                    else -> config.log.info("$status: ${request.requestLine}")
                }
            }
            ApplicationCallResult.Unhandled -> config.log.info("<Unhandled>: ${request.requestLine}")
            ApplicationCallResult.Asynchronous -> config.log.info("<Async>: ${request.requestLine}")
        }
    }

    /**
     * Called by host when Application is terminated
     */
    public open fun dispose() {
    }
}

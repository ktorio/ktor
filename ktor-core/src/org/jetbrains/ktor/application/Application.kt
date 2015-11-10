package org.jetbrains.ktor.application

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*

/** Current executing application
 */
public open class Application(val config: ApplicationConfig) {
    private val handler: Interceptable1<ApplicationRequestContext, ApplicationRequestStatus> = Interceptable1 {
        ApplicationRequestStatus.Unhandled
    }

    public fun intercept(interceptor: ApplicationRequestContext.(ApplicationRequestContext.() -> ApplicationRequestStatus) -> ApplicationRequestStatus) {
        handler.intercept(interceptor)
    }

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

    public open fun dispose() {
    }
}

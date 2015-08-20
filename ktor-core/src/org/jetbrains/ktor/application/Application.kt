package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*

/** Current executing application
 */
public open class Application(val config: ApplicationConfig) {
    private val handler: Interceptable1<ApplicationRequestContext, ApplicationRequestStatus> = Interceptable1 {
        ApplicationRequestStatus.Unhandled
    }

    public fun intercept(interceptor: (ApplicationRequestContext, (ApplicationRequestContext) -> ApplicationRequestStatus) -> ApplicationRequestStatus) {
        handler.intercept(interceptor)
    }

    public fun handle(context: ApplicationRequestContext): ApplicationRequestStatus {
        val result = handler.call(context)
        when (result) {
            ApplicationRequestStatus.Handled -> config.log.info("${context.response.status()}: ${context.request.requestLine}")
            ApplicationRequestStatus.Unhandled -> config.log.info("<Unhandled>: ${context.request.requestLine}")
            ApplicationRequestStatus.Asynchronous -> config.log.info("<Async>: ${context.request.requestLine}")
        }
        return result
    }

    public open fun dispose() {
    }
}

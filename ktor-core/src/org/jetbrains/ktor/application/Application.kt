package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*

/** Current executing application
 */
public open class Application(val config: ApplicationConfig) {

    public val handler: Interceptable1<ApplicationRequestContext, ApplicationRequestStatus> = Interceptable1 {
        ApplicationRequestStatus.Unhandled
    }

    public fun handle(context: ApplicationRequestContext): ApplicationRequestStatus {
        var status = 0
        context.response.status.intercept { code, next ->
            status = code
            next(code)
        }
        val result = handler.call(context)
        if (result == ApplicationRequestStatus.Handled) {
            config.log.info("$status: ${context.request.requestLine}")
        } else {
            config.log.info("<Unhandled>: ${context.request.requestLine}")
        }
        return result
    }

    public open fun dispose() {
    }
}

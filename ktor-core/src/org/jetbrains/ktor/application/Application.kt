package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*

/** Current executing application
 */
public open class Application(val config: ApplicationConfig) {

    public val handler: Interceptable1<ApplicationRequestContext, ApplicationRequestStatus> = Interceptable1 {
        ApplicationRequestStatus.Unhandled
    }

    public fun handle(context: ApplicationRequestContext): ApplicationRequestStatus {
        val result = handler.call(context)
        config.log.info("$result: ${context.request.requestLine}")
        return result
    }

    public open fun dispose() {
    }
}

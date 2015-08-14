package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*

/** Current executing application
 */
public open class Application(val config: ApplicationConfig) {

    public val handler: Interceptable1<ApplicationRequest, ApplicationRequestStatus> = Interceptable1 {
        ApplicationRequestStatus.Unhandled
    }

    public fun handle(request: ApplicationRequest): ApplicationRequestStatus {
        val result = handler.call(request)
        config.log.info("$result: ${request.requestLine}")
        return result
    }

    public open fun dispose() {
    }
}

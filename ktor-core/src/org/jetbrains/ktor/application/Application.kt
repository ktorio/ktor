package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.util.*

/**
 * Represents configured and running web application, capable of handling requests
 */
public open class Application(val config: ApplicationConfig) : InterceptApplicationCall<ApplicationCall> {
    /**
     * Provides common place to store application-wide attributes
     */
    val attributes = Attributes()

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
    public fun handle(call: ApplicationCall): ApplicationCallResult = handler.execute(call)

    /**
     * Called by host when Application is terminated
     */
    public open fun dispose() {
    }
}

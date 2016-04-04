package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

/**
 * Represents configured and running web application, capable of handling requests
 */
public open class Application(val config: ApplicationConfig) : InterceptApplicationCall {
    /**
     * Provides common place to store application-wide attributes
     */
    val attributes = Attributes()

    val pipeline = Pipeline<ApplicationCall>()

    /**
     * Installs interceptor into the current Application handling chain
     */
    override fun intercept(interceptor: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit) {
        pipeline.intercept(interceptor)
    }

    /**
     * Handles HTTP request coming from the host using interceptors
     */
    fun handle(call: ApplicationCall) = call.execute(pipeline, call)

    /**
     * Called by host when Application is terminated
     */
    public open fun dispose() {
    }
}

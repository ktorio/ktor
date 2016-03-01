package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

/**
 * Represents configured and running web application, capable of handling requests
 */
public open class Application(val config: ApplicationConfig) : InterceptApplicationCall<ApplicationCall> {
    /**
     * Provides common place to store application-wide attributes
     */
    val attributes = Attributes()

    private val pipeline = Pipeline<ApplicationCall>()

    /**
     * Installs interceptor into the current Application handling chain
     */
    override fun intercept(interceptor: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit) {
        pipeline.intercept(interceptor)
    }

    /**
     * Handles HTTP request coming from the host using interceptors
     */
    public fun handle(call: ApplicationCall): PipelineExecution.State {
        return pipeline.execute(call).state
    }

    /**
     * Called by host when Application is terminated
     */
    public open fun dispose() {
    }
}

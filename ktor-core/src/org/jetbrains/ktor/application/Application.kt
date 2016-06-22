package org.jetbrains.ktor.application

import org.jetbrains.ktor.util.*
import java.util.concurrent.*

/**
 * Represents configured and running web application, capable of handling requests
 */
open class Application(val environment: ApplicationEnvironment) : ApplicationCallPipeline() {
    var closeHooks = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Provides common place to store application-wide attributes
     */
    val attributes = Attributes()

    /**
     * Called by host when [Application] is terminated
     */
    open fun dispose() {
        closeHooks.forEach {
            try {
                it()
            } catch (ignore: Throwable) {
            }
        }
    }
}

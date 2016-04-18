package org.jetbrains.ktor.application

import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

/**
 * Represents configured and running web application, capable of handling requests
 */
open class Application(val config: ApplicationConfig) : Pipeline<ApplicationCall>() {
    /**
     * Provides common place to store application-wide attributes
     */
    val attributes = Attributes()

    /**
     * Called by host when Application is terminated
     */
    open fun dispose() {
    }
}

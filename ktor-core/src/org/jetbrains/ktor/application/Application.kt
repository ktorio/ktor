package org.jetbrains.ktor.application

import java.util.*

/** Current executing application
 */
public open class Application(val config: ApplicationConfig) {

    private val interceptors = ArrayList<(ApplicationRequest, (ApplicationRequest) -> Boolean) -> Boolean>()
    public fun intercept(handler: (request: ApplicationRequest, proceed: (ApplicationRequest) -> Boolean) -> Boolean) {
        interceptors.add(handler)
    }

    public fun handle(request: ApplicationRequest): Boolean {
        fun handle(index: Int, request: ApplicationRequest): Boolean {
            return if (index < interceptors.size()) {
                interceptors[index](request) { handle(index + 1, it) }
            } else {
                false
            }
        }

        return handle(0, request)
    }

    public open fun dispose() {

    }
}


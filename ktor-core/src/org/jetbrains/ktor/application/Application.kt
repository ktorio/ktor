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
        val queryString = request.queryString()
        val requestLogString = "${request.httpMethod} -- ${request.uri}${if (queryString.isNotEmpty()) "?$queryString" else ""}"

        fun handle(index: Int, request: ApplicationRequest): Boolean {
            return if (index < interceptors.size()) {
                interceptors[index](request) { handle(index + 1, it) }
            } else {
                false
            }
        }

        val result = handle(0, request)
        config.log.info("$requestLogString -- ${ if(result) "OK" else "FAIL"}")
        return result
    }

    public open fun dispose() {

    }
}


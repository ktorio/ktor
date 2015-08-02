package org.jetbrains.ktor.application

import java.util.*

/** Current executing application
 */
public open class Application(val config: ApplicationConfig) {

    private val interceptors = ArrayList<(ApplicationRequest, (ApplicationRequest) -> ApplicationRequestStatus) -> ApplicationRequestStatus>()
    public fun intercept(handler: (request: ApplicationRequest, proceed: (ApplicationRequest) -> ApplicationRequestStatus) -> ApplicationRequestStatus) {
        interceptors.add(handler)
    }

    public fun handle(request: ApplicationRequest): ApplicationRequestStatus {
        val queryString = request.queryString()
        val requestLogString = "${request.httpMethod} -- ${request.uri}"

        fun handle(index: Int, request: ApplicationRequest): ApplicationRequestStatus = when (index) {
            in interceptors.indices -> {
                val interceptor = interceptors[index]
                val proceed: (ApplicationRequest) -> ApplicationRequestStatus = { augmentedRequest -> handle(index + 1, augmentedRequest) }
                interceptor(request, proceed)
            }
            else -> ApplicationRequestStatus.Unhandled
        }

        val result = handle(0, request)
        config.log.info("$requestLogString -- $result")
        return result
    }

    public open fun dispose() {
    }
}

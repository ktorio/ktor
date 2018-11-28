package io.ktor.client.features.observer

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.response.*
import io.ktor.util.*
import kotlinx.coroutines.*

/**
 * [ResponseObserver] callback.
 */
typealias ResponseHandler = suspend (HttpResponse) -> Unit

/**
 * Observe response feature.
 */
class ResponseObserver(
    private val responseHandler: ResponseHandler
) {
    class Config {
        internal var responseHandler: ResponseHandler = {}

        /**
         * Set response handler for logging.
         */
        fun onResponse(block: ResponseHandler) {
            responseHandler = block
        }
    }

    companion object Feature : HttpClientFeature<Config, ResponseObserver> {

        override val key: AttributeKey<ResponseObserver> = AttributeKey("BodyInterceptor")

        override fun prepare(block: Config.() -> Unit): ResponseObserver =
            ResponseObserver(Config().apply(block).responseHandler)

        override fun install(feature: ResponseObserver, scope: HttpClient) {

            scope.receivePipeline.intercept(HttpReceivePipeline.Before) { response ->
                val (loggingContent, responseContent) = response.content.split(scope)

                launch {
                    val callForLog = context.wrapWithContent(loggingContent, shouldCloseOrigin = false)
                    feature.responseHandler(callForLog.response)
                }

                val newCall = context.wrapWithContent(responseContent, shouldCloseOrigin = true)
                context.response = newCall.response
                context.request = newCall.request

                proceedWith(context.response)
            }
        }
    }
}

/**
 * Install [ResponseObserver] feature in client.
 */
fun HttpClientConfig<*>.ResponseObserver(block: ResponseHandler) {
    install(ResponseObserver) {
        responseHandler = block
    }
}

package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.util.*

/**
 * [HttpClient] feature that handles http redirect
 */
class HttpRedirect(
    val maxJumps: Int
) {

    class Config {
        var maxJumps: Int = 20
    }

    companion object Feature : HttpClientFeature<Config, HttpRedirect> {
        override val key: AttributeKey<HttpRedirect> = AttributeKey("HttpRedirect")

        private val Redirect = PipelinePhase("RedirectPhase")

        override fun prepare(block: Config.() -> Unit): HttpRedirect =
            HttpRedirect(Config().apply(block).maxJumps)

        override fun install(feature: HttpRedirect, scope: HttpClient) {
            scope.requestPipeline.insertPhaseBefore(HttpRequestPipeline.Send, Redirect)
            scope.requestPipeline.intercept(Redirect) { body ->
                repeat(feature.maxJumps) {
                    val call = scope.sendPipeline.execute(context, body) as HttpClientCall

                    if (!call.response.status.isRedirect()) {
                        finish()
                        proceedWith(call)
                        return@intercept
                    }

                    val location = call.response.headers[HttpHeaders.Location]
                    location?.let { context.url.takeFrom(it) }
                }

                throw RedirectException(context.build(), "Redirect limit ${feature.maxJumps} exceeded")
            }
        }
    }
}

private fun HttpStatusCode.isRedirect(): Boolean = when (value) {
    HttpStatusCode.MovedPermanently.value,
    HttpStatusCode.Found.value,
    HttpStatusCode.TemporaryRedirect.value,
    HttpStatusCode.PermanentRedirect.value -> true
    else -> false
}

class RedirectException(val request: HttpRequestData, cause: String) : IllegalStateException(cause)

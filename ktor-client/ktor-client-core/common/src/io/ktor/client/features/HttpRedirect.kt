package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.pipeline.*
import io.ktor.util.*

/**
 * [HttpClient] feature that handles http redirect
 */
class HttpRedirect {

    companion object Feature : HttpClientFeature<Unit, HttpRedirect> {
        override val key: AttributeKey<HttpRedirect> = AttributeKey("HttpRedirect")

        override fun prepare(block: Unit.() -> Unit): HttpRedirect = HttpRedirect()

        override fun install(feature: HttpRedirect, scope: HttpClient) {
            scope.feature(HttpSend)!!.intercept { origin ->
                handleCall(origin)
            }
        }

        private suspend fun Sender.handleCall(origin: HttpClientCall): HttpClientCall {
            if (!origin.response.status.isRedirect()) return origin

            var call = origin
            while (true) {
                val location = call.response.headers[HttpHeaders.Location]

                call = execute(HttpRequestBuilder().apply {
                    takeFrom(origin.request)
                    url.parameters.clear()

                    location?.let { url.takeFrom(it) }
                })

                if (!call.response.status.isRedirect()) return call
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

@Deprecated(
    "Not thrown anymore. Use SendCountExceedException",
    replaceWith = ReplaceWith(
        "SendCountExceedException",
        "io.ktor.client.features.SendCountExceedException"
    )
)
@Suppress("KDocMissingDocumentation")
class RedirectException(val request: HttpRequest, cause: String) : IllegalStateException(cause)

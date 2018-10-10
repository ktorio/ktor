package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.atomicfu.*

/**
 * Terminate response pipeline with fail status code in response.
 */
class ExpectSuccess(
) {


    companion object : HttpClientFeature<Unit, ExpectSuccess> {
        override val key: AttributeKey<ExpectSuccess> = AttributeKey("ExpectSuccess")

        override fun prepare(block: Unit.() -> Unit): ExpectSuccess = ExpectSuccess()

        override fun install(feature: ExpectSuccess, scope: HttpClient) {
            scope.responsePipeline.intercept(HttpResponsePipeline.Receive) {
                val response = context.response
                if (response.status.value >= 300) throw BadResponseStatus(response.status, response)
            }
        }
    }
}

class BadResponseStatus(
    val statusCode: HttpStatusCode,
    val response: HttpResponse
) : IllegalStateException()

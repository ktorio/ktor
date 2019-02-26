package io.ktor.client.features

import io.ktor.client.*
import io.ktor.client.response.*

/**
 * Default response validation.
 * Check the response status code in range (0..299).
 */
fun HttpClientConfig<*>.addDefaultResponseValidation() {
    HttpResponseValidator {
        validateResponse { response ->
            val statusCode = response.status.value
            when (statusCode) {
                in 300..399 -> throw RedirectResponseException(response)
                in 400..499 -> throw ClientRequestException(response)
                in 500..599 -> throw ServerResponseException(response)
            }

            if (statusCode >= 600) {
                throw ResponseException(response)
            }
        }
    }
}

/**
 * Base for default response exceptions.
 * @param response: origin response
 */
open class ResponseException(
    val response: HttpResponse
) : IllegalStateException("Bad response: $response")

/**
 * Unhandled redirect exception.
 */
@Suppress("KDocMissingDocumentation")
class RedirectResponseException(response: HttpResponse) : ResponseException(response) {
    override val message: String? = "Unhandled redirect: ${response.call.request.url}"
}

/**
 * Server error exception.
 */
@Suppress("KDocMissingDocumentation")
class ServerResponseException(
    response: HttpResponse
) : ResponseException(response) {
    override val message: String? = "Server error(${response.call.request.url}: ${response.status}."
}

/**
 * Bad client request exception.
 */
@Suppress("KDocMissingDocumentation")
class ClientRequestException(
    response: HttpResponse
) : ResponseException(response) {
    override val message: String? = "Client request(${response.call.request.url}) invalid: ${response.status}"
}



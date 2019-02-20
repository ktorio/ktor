package io.ktor.client.features.auth

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.content.*
import io.ktor.pipeline.*
import io.ktor.util.*

/**
 * Authentication provider interface.
 */
interface AuthProvider {
    /**
     * Check if current provider is applicable to the request.
     */
    fun isApplicable(auth: HttpAuthHeader): Boolean

    /**
     * Add authentication method headers and creds.
     */
    suspend fun addRequestHeaders(request: HttpRequestBuilder)
}

/**
 * Client authentication feature.
 * [providers] - list of auth providers to use.
 */
@Suppress("KDocMissingDocumentation")
class Auth(
    val providers: MutableList<AuthProvider> = mutableListOf()
) {

    companion object Feature : HttpClientFeature<Auth, Auth> {
        override val key: AttributeKey<Auth> = AttributeKey("DigestAuth")

        override fun prepare(block: Auth.() -> Unit): Auth {
            return Auth().apply(block)
        }

        override fun install(feature: Auth, scope: HttpClient) {
            scope.feature(HttpSend)!!.intercept { origin ->
                var call = origin

                while (call.response.status.value == HttpStatusCode.Unauthorized.value) {
                    val headerValue = call.response.headers[HttpHeaders.WWWAuthenticate] ?: return@intercept call
                    val authHeader = parseAuthorizationHeader(headerValue) ?: return@intercept call
                    val provider = feature.providers.find { it.isApplicable(authHeader) } ?: return@intercept call

                    val request = HttpRequestBuilder()
                    request.takeFrom(call.request)
                    provider.addRequestHeaders(request)

                    call = execute(request)
                }

                return@intercept call
            }
        }
    }
}

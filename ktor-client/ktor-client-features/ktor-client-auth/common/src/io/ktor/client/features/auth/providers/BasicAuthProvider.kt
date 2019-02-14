package io.ktor.client.features.auth.providers

import io.ktor.client.features.auth.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*

/**
 * Add [BasicAuthProvider] to client [Auth] providers.
 */
fun Auth.basic(block: BasicAuthConfig.() -> Unit) {
    with(BasicAuthConfig().apply(block)) {
        providers.add(BasicAuthProvider(username, password, realm))
    }
}

/**
 * [BasicAuthProvider] configuration.
 */
class BasicAuthConfig {
    /**
     * Required: The username of the basic auth.
     */
    lateinit var username: String

    /**
     * Required: The password of the basic auth.
     */
    lateinit var password: String

    /**
     * Optional: current provider realm
     */
    var realm: String? = null
}

/**
 * Client basic authentication provider.
 */
class BasicAuthProvider(
    private val username: String,
    private val password: String,
    private val realm: String?
) : AuthProvider {
    private val defaultCharset = Charset.forName("ISO_8859_1")

    override fun isApplicable(auth: HttpAuthHeader): Boolean {
        if (auth.authScheme != AuthScheme.Basic) return false

        if (realm != null) {
            if (auth !is HttpAuthHeader.Parameterized) return false
            return auth.parameter("realm") == realm
        }

        return true
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder) {
        request.headers[HttpHeaders.Authorization] = constructBasicAuthValue(username, password)
    }

    private fun constructBasicAuthValue(username: String, password: String): String {
        val authString = "$username:$password"
        val authBuf = authString.toByteArray(defaultCharset).encodeBase64()

        return "Basic $authBuf"
    }
}

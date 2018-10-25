package io.ktor.auth

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.util.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.io.*
import java.lang.Exception
import java.net.*
import java.time.*
import java.util.*
import javax.crypto.*
import javax.crypto.spec.*

internal suspend fun PipelineContext<Unit, ApplicationCall>.oauth1a(
    client: HttpClient, dispatcher: CoroutineDispatcher,
    providerLookup: ApplicationCall.() -> OAuthServerSettings?,
    urlProvider: ApplicationCall.(OAuthServerSettings) -> String
) {
    val provider = call.providerLookup()
    if (provider is OAuthServerSettings.OAuth1aServerSettings) {
        val token = call.oauth1aHandleCallback()
        withContext(dispatcher) {
            val callbackRedirectUrl = call.urlProvider(provider)
            if (token == null) {
                val t = simpleOAuth1aStep1(client, provider, callbackRedirectUrl)
                call.redirectAuthenticateOAuth1a(provider, t)
            } else {
                try {
                    val accessToken = requestOAuth1aAccessToken(client, provider, token)
                    call.authentication.principal(accessToken)
                } catch (ioe: IOException) {
                    call.oauthHandleFail(callbackRedirectUrl)
                }
            }
        }
    }
}

internal fun ApplicationCall.oauth1aHandleCallback(): OAuthCallback.TokenPair? {
    val token = parameters[HttpAuthHeader.Parameters.OAuthToken]
    val verifier = parameters[HttpAuthHeader.Parameters.OAuthVerifier]

    return when {
        token != null && verifier != null -> OAuthCallback.TokenPair(token, verifier)
        else -> null
    }
}

internal suspend fun simpleOAuth1aStep1(
    client: HttpClient,
    settings: OAuthServerSettings.OAuth1aServerSettings,
    callbackUrl: String,
    nonce: String = generateNonce(),
    extraParameters: List<Pair<String, String>> = emptyList()
): OAuthCallback.TokenPair = simpleOAuth1aStep1(
    client,
    settings.consumerSecret + "&",
    settings.requestTokenUrl,
    callbackUrl,
    settings.consumerKey,
    nonce,
    extraParameters
)

private suspend fun simpleOAuth1aStep1(
    client: HttpClient,
    secretKey: String,
    baseUrl: String,
    callback: String,
    consumerKey: String,
    nonce: String = generateNonce(),
    extraParameters: List<Pair<String, String>> = emptyList()
): OAuthCallback.TokenPair {
    val authHeader = createObtainRequestTokenHeader(
        callback = callback,
        consumerKey = consumerKey,
        nonce = nonce
    ).sign(HttpMethod.Post, baseUrl, secretKey, extraParameters)

    val response = client.call(URL(baseUrl.appendUrlParameters(extraParameters.formUrlEncode()))) {
        method = HttpMethod.Post
        header(HttpHeaders.Authorization, authHeader.render(HeaderValueEncoding.URI_ENCODE))
        header(HttpHeaders.Accept, ContentType.Any.toString())
    }.response

    val body = response.readText()

    try {
        if (response.status != HttpStatusCode.OK) {
            throw IOException("Bad response: $response")
        }

        val parameters = body.parseUrlEncodedParameters()
        require(parameters[HttpAuthHeader.Parameters.OAuthCallbackConfirmed] == "true") {
            "Response parameter oauth_callback_confirmed should be true"
        }

        return OAuthCallback.TokenPair(
            parameters[HttpAuthHeader.Parameters.OAuthToken]!!,
            parameters[HttpAuthHeader.Parameters.OAuthTokenSecret]!!
        )
    } catch (e: Throwable) {
        throw IOException("Failed to acquire request token due to $body", e)
    } finally {
        response.close()
    }
}

internal suspend fun ApplicationCall.redirectAuthenticateOAuth1a(
    settings: OAuthServerSettings.OAuth1aServerSettings,
    requestToken: OAuthCallback.TokenPair
) {
    redirectAuthenticateOAuth1a(settings.authorizeUrl, requestToken.token)
}

internal suspend fun ApplicationCall.redirectAuthenticateOAuth1a(authenticateUrl: String, requestToken: String) {
    val url = authenticateUrl.appendUrlParameters(
        "${HttpAuthHeader.Parameters.OAuthToken}=${requestToken.encodeURLParameter()}"
    )
    respondRedirect(url)
}

internal suspend fun requestOAuth1aAccessToken(
    client: HttpClient,
    settings: OAuthServerSettings.OAuth1aServerSettings,
    callbackResponse: OAuthCallback.TokenPair,
    nonce: String = generateNonce(),
    extraParameters: Map<String, String> = emptyMap()
): OAuthAccessTokenResponse.OAuth1a = requestOAuth1aAccessToken(
    client,
    settings.consumerSecret + "&", // TODO??
    settings.accessTokenUrl,
    settings.consumerKey,
    token = callbackResponse.token,
    verifier = callbackResponse.tokenSecret,
    nonce = nonce,
    extraParameters = extraParameters
)

private suspend fun requestOAuth1aAccessToken(
    client: HttpClient,
    secretKey: String,
    baseUrl: String,
    consumerKey: String,
    token: String,
    verifier: String,
    nonce: String = generateNonce(),
    extraParameters: Map<String, String> = emptyMap()
): OAuthAccessTokenResponse.OAuth1a {
    val params = listOf(HttpAuthHeader.Parameters.OAuthVerifier to verifier) + extraParameters.toList()
    val authHeader = createUpgradeRequestTokenHeader(consumerKey, token, nonce)
        .sign(HttpMethod.Post, baseUrl, secretKey, params)

    val response = client.call(URL(baseUrl)) {
        method = HttpMethod.Post

        header(HttpHeaders.Authorization, authHeader.render(HeaderValueEncoding.URI_ENCODE))
        header(HttpHeaders.Accept, "*/*")
        // some of really existing OAuth servers don't support other accept header values so keep it

        body = WriterContent(
            { params.formUrlEncodeTo(this) },
            ContentType.Application.FormUrlEncoded
        )
    }.response

    val body = response.readText()
    try {
        val parameters = body.parseUrlEncodedParameters()
        return OAuthAccessTokenResponse.OAuth1a(
            parameters[HttpAuthHeader.Parameters.OAuthToken] ?: throw OAuth1aException.MissingTokenException(),
            parameters[HttpAuthHeader.Parameters.OAuthTokenSecret] ?: throw OAuth1aException.MissingTokenException(),
            parameters
        )
    } catch (cause: OAuth1aException) {
        throw cause
    } catch (cause: Throwable) {
        throw IOException("Failed to acquire request token due to $body", cause)
    } finally {
        response.close()
    }
}

@Suppress("KDocMissingDocumentation")
@Deprecated("Use createObtainRequestTokenHeader instead",
    ReplaceWith("createObtainRequestTokenHeader(callback, consumerKey, nonce, timestamp)")
)
@KtorExperimentalAPI
fun obtainRequestTokenHeader(
    callback: String,
    consumerKey: String,
    nonce: String,
    timestamp: LocalDateTime = LocalDateTime.now()
): HttpAuthHeader.Parameterized = createObtainRequestTokenHeader(callback, consumerKey, nonce, timestamp)

/**
 * Create an HTTP auth header for OAuth1a obtain token request
 */
@KtorExperimentalAPI
fun createObtainRequestTokenHeader(
    callback: String,
    consumerKey: String,
    nonce: String,
    timestamp: LocalDateTime = LocalDateTime.now()
): HttpAuthHeader.Parameterized = HttpAuthHeader.Parameterized(
    authScheme = AuthScheme.OAuth,
    parameters = mapOf(
        HttpAuthHeader.Parameters.OAuthCallback to callback,
        HttpAuthHeader.Parameters.OAuthConsumerKey to consumerKey,
        HttpAuthHeader.Parameters.OAuthNonce to nonce,
        HttpAuthHeader.Parameters.OAuthSignatureMethod to "HMAC-SHA1",
        HttpAuthHeader.Parameters.OAuthTimestamp to timestamp.toEpochSecond(ZoneOffset.UTC).toString(),
        HttpAuthHeader.Parameters.OAuthVersion to "1.0"
    )
)

/**
 * Create an HTTP auth header for OAuth1a upgrade token request
 */
@Deprecated("Use createUpgradeRequestTokenHeader instead",
    ReplaceWith("createUpgradeRequestTokenHeader(consumerKey, token, nonce, timestamp)")
)
@KtorExperimentalAPI
fun upgradeRequestTokenHeader(
    consumerKey: String,
    token: String,
    nonce: String,
    timestamp: LocalDateTime = LocalDateTime.now()
): HttpAuthHeader.Parameterized = createUpgradeRequestTokenHeader(consumerKey, token, nonce, timestamp)

/**
 * Create an HTTP auth header for OAuth1a upgrade token request
 */
@KtorExperimentalAPI
fun createUpgradeRequestTokenHeader(
    consumerKey: String,
    token: String,
    nonce: String,
    timestamp: LocalDateTime = LocalDateTime.now()
): HttpAuthHeader.Parameterized = HttpAuthHeader.Parameterized(
    authScheme = AuthScheme.OAuth,
    parameters = mapOf(
        HttpAuthHeader.Parameters.OAuthConsumerKey to consumerKey,
        HttpAuthHeader.Parameters.OAuthToken to token,
        HttpAuthHeader.Parameters.OAuthNonce to nonce,
        HttpAuthHeader.Parameters.OAuthSignatureMethod to "HMAC-SHA1",
        HttpAuthHeader.Parameters.OAuthTimestamp to timestamp.toEpochSecond(ZoneOffset.UTC).toString(),
        HttpAuthHeader.Parameters.OAuthVersion to "1.0"
    )
)

/**
 * Sign an HTTP auth header
 */
@KtorExperimentalAPI
fun HttpAuthHeader.Parameterized.sign(
    method: HttpMethod,
    baseUrl: String,
    key: String,
    parameters: List<Pair<String, String>>
): HttpAuthHeader.Parameterized = withParameter(
    HttpAuthHeader.Parameters.OAuthSignature,
    signatureBaseString(this, method, baseUrl, parameters.toHeaderParamsList()).hmacSha1(key)
)

/**
 * Build an OAuth1a signature base string as per RFC
 */
@KtorExperimentalAPI
fun signatureBaseString(
    header: HttpAuthHeader.Parameterized,
    method: HttpMethod,
    baseUrl: String,
    parameters: List<HeaderValueParam>
): String = listOf(method.value.toUpperCase(), baseUrl, parametersString(header.parameters + parameters))
    .joinToString("&") { it.encodeURLParameter() }

private fun String.hmacSha1(key: String): String {
    val keySpec = SecretKeySpec(key.toByteArray(), "HmacSHA1")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(keySpec)

    return Base64.getEncoder().encodeToString(mac.doFinal(this.toByteArray()))
}

private fun parametersString(parameters: List<HeaderValueParam>): String =
    parameters.map { it.name.encodeURLParameter() to it.value.encodeURLParameter() }
        .sortedWith(compareBy<Pair<String, String>> { it.first }.then(compareBy { it.second }))
        .joinToString("&") { "${it.first}=${it.second}" }


/**
 * Represents an OAuth1a server error
 */
@KtorExperimentalAPI
sealed class OAuth1aException(message: String) : Exception(message) {

    /**
     * Thrown when an OAuth1a server didn't provide access token
     */
    class MissingTokenException() : OAuth1aException("The OAuth1a server didn't provide access token")

    /**
     * Represents any other OAuth1a error
     */
    @KtorExperimentalAPI
    class UnknownException(message: String) : OAuth1aException(message)
}

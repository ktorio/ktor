package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.net.*
import java.time.*
import java.util.*
import java.util.concurrent.*
import javax.crypto.*
import javax.crypto.spec.*

suspend internal fun PipelineContext<Unit>.oauth1a(client: HttpClient, exec: ExecutorService,
                                                              providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                                              urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    val provider = call.providerLookup()
    if (provider is OAuthServerSettings.OAuth1aServerSettings) {
        val token = call.oauth1aHandleCallback()
        runAsync(exec) {
            val callbackRedirectUrl = call.urlProvider(provider)
            if (token == null) {
                val t = simpleOAuth1aStep1(client, provider, callbackRedirectUrl)
                call.redirectAuthenticateOAuth1a(provider, t)
            } else {
                try {
                    val accessToken = simpleOAuth1aStep2(client, provider, token)
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

internal suspend fun simpleOAuth1aStep1(client: HttpClient, settings: OAuthServerSettings.OAuth1aServerSettings, callbackUrl: String, nonce: String = nextNonce(), extraParameters: List<Pair<String, String>> = emptyList()): OAuthCallback.TokenPair {
    return simpleOAuth1aStep1(
            client,
            settings.consumerSecret + "&",
            settings.requestTokenUrl,
            callbackUrl,
            settings.consumerKey,
            nonce,
            extraParameters
    )
}

private suspend fun simpleOAuth1aStep1(client: HttpClient, secretKey: String, baseUrl: String, callback: String, consumerKey: String, nonce: String = nextNonce(), extraParameters: List<Pair<String, String>> = emptyList()): OAuthCallback.TokenPair {
    val authHeader = obtainRequestTokenHeader(
            callback = callback,
            consumerKey = consumerKey,
            nonce = nonce
    ).sign(HttpMethod.Post, baseUrl, secretKey, extraParameters)

    val response = client.request(URL(baseUrl.appendUrlParameters(extraParameters.formUrlEncode()))) {
        method = HttpMethod.Post
        header(HttpHeaders.Authorization, authHeader.render(HeaderValueEncoding.URI_ENCODE))
        header(HttpHeaders.Accept, ContentType.Any.toString())
        body = {}
    }
    try {
        if (response.status.value != HttpStatusCode.OK.value) {
            throw IOException("Bad response: ${response.status}")
        }

        val responseText = response.stream.reader().readText().parseUrlEncodedParameters()
        require(responseText[HttpAuthHeader.Parameters.OAuthCallbackConfirmed] == "true") { "Response parameter oauth_callback_confirmed should be true" }

        return OAuthCallback.TokenPair(responseText[HttpAuthHeader.Parameters.OAuthToken]!!, responseText[HttpAuthHeader.Parameters.OAuthTokenSecret]!!)
    } catch (e: Throwable) {
        throw IOException("Failed to acquire request token due to ${response.stream.reader().readText()}", e)
    } finally {
        response.close()
    }
}

suspend internal fun ApplicationCall.redirectAuthenticateOAuth1a(settings: OAuthServerSettings.OAuth1aServerSettings, requestToken: OAuthCallback.TokenPair) {
    redirectAuthenticateOAuth1a(settings.authorizeUrl, requestToken.token)
}

suspend internal fun ApplicationCall.redirectAuthenticateOAuth1a(authenticateUrl: String, requestToken: String) {
    val url = authenticateUrl.appendUrlParameters("${HttpAuthHeader.Parameters.OAuthToken}=${encodeURLQueryComponent(requestToken)}")
    respondRedirect(url)
}

internal suspend fun simpleOAuth1aStep2(client: HttpClient, settings: OAuthServerSettings.OAuth1aServerSettings, callbackResponse: OAuthCallback.TokenPair, nonce: String = nextNonce(), extraParameters: Map<String, String> = emptyMap()): OAuthAccessTokenResponse.OAuth1a {
    return simpleOAuth1aStep2(
            client,
            settings.consumerSecret + "&", // TODO??
            settings.accessTokenUrl,
            settings.consumerKey,
            token = callbackResponse.token,
            verifier = callbackResponse.tokenSecret,
            nonce = nonce,
            extraParameters = extraParameters
    )
}

private suspend fun simpleOAuth1aStep2(client: HttpClient, secretKey: String, baseUrl: String, consumerKey: String, token: String, verifier: String, nonce: String = nextNonce(), extraParameters: Map<String, String> = emptyMap()): OAuthAccessTokenResponse.OAuth1a {
    val params = listOf(
            HttpAuthHeader.Parameters.OAuthVerifier to verifier
    ) + extraParameters.toList()
    val authHeader = upgradeRequestTokenHeader(consumerKey, token, nonce).sign(HttpMethod.Post, baseUrl, secretKey, params)

    val response = client.request(URL(baseUrl)) {
        method = HttpMethod.Post

        header(HttpHeaders.Authorization, authHeader.render(HeaderValueEncoding.URI_ENCODE))
        header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
        header(HttpHeaders.Accept, "*/*")

        body = {
            it.writer().use { writer ->
                params.formUrlEncodeTo(writer)
            }
        }
    }

    try {
        val responseText = response.stream.reader().readText().parseUrlEncodedParameters()
        return OAuthAccessTokenResponse.OAuth1a(responseText[HttpAuthHeader.Parameters.OAuthToken]!!, responseText[HttpAuthHeader.Parameters.OAuthTokenSecret]!!, responseText)
    } catch (e: Throwable) {
        throw IOException("Failed to acquire request token due to ${response.stream.reader().readText()}", e)
    } finally {
        response.close()
    }
}

fun obtainRequestTokenHeader(
        callback: String,
        consumerKey: String,
        nonce: String,
        timestamp: LocalDateTime = LocalDateTime.now()
) = HttpAuthHeader.Parameterized(
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

fun upgradeRequestTokenHeader(
        consumerKey: String,
        token: String,
        nonce: String,
        timestamp: LocalDateTime = LocalDateTime.now()
) = HttpAuthHeader.Parameterized(
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

fun HttpAuthHeader.Parameterized.sign(method: HttpMethod, baseUrl: String, key: String, parameters: List<Pair<String, String>>) =
        withParameter(HttpAuthHeader.Parameters.OAuthSignature, signatureBaseString(this, method, baseUrl, parameters.toHeaderParamsList()).hmacSha1(key))

private fun String.hmacSha1(key: String): String {
    val keySpec = SecretKeySpec(key.toByteArray(), "HmacSHA1")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(keySpec)

    return Base64.getEncoder().encodeToString(mac.doFinal(this.toByteArray()))
}

private fun parametersString(parameters: List<HeaderValueParam>) =
        parameters
                .map { it.name.percentEncode() to it.value.percentEncode() }
                .sortedWith(compareBy<Pair<String, String>> { it.first }.then(compareBy { it.second }))
                .joinToString("&") { "${it.first}=${it.second}" }

fun signatureBaseString(header: HttpAuthHeader.Parameterized, method: HttpMethod, baseUrl: String, parameters: List<HeaderValueParam>) =
        listOf(method.value.toUpperCase(), baseUrl, parametersString(header.parameters + parameters))
                .map { it.percentEncode() }
                .joinToString("&")

private fun String.percentEncode() = encodeURLPart(this)
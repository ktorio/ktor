package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import org.json.simple.*
import java.io.*
import java.net.*
import java.security.*
import java.time.*
import java.util.*
import java.util.concurrent.*
import javax.crypto.*
import javax.crypto.spec.*
import kotlin.reflect.*

enum class OAuthVersion {
    V10a, V20
}

sealed class OAuthServerSettings(val name: String, val version: OAuthVersion) {
    class OAuth1aServerSettings(
            name: String,
            val requestTokenUrl: String,
            val authorizeUrl: String,
            val accessTokenUrl: String,

            val consumerKey: String,
            val consumerSecret: String
    ) : OAuthServerSettings(name, OAuthVersion.V10a)

    class OAuth2ServerSettings(
            name: String,
            val authorizeUrl: String,
            val accessTokenUrl: String,
            val requestMethod: HttpMethod = HttpMethod.Get,

            val clientId: String,
            val clientSecret: String,
            val defaultScopes: List<String> = emptyList()
    ) : OAuthServerSettings(name, OAuthVersion.V20)
}

sealed class OAuthCallback {
    class TokenPair(val token: String, val tokenSecret: String) : OAuthCallback()
    class TokenSingle(val token: String, val state: String) : OAuthCallback()
}

sealed class OAuthAccessTokenResponse : Principal {
    class OAuth1a(val token: String, val tokenSecret: String, val extraParameters: ValuesMap = ValuesMap.Empty) : OAuthAccessTokenResponse()
    class OAuth2(val accessToken: String, val tokenType: String, val expiresIn: Long, val refreshToken: String?, val extraParameters: ValuesMap = ValuesMap.Empty) : OAuthAccessTokenResponse()
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

private fun ApplicationResponse.redirectAuthenticateOAuth1a(settings: OAuthServerSettings.OAuth1aServerSettings, requestToken: OAuthCallback.TokenPair): ApplicationRequestStatus =
    redirectAuthenticateOAuth1a(settings.authorizeUrl, requestToken.token)

private fun ApplicationResponse.redirectAuthenticateOAuth1a(authenticateUrl: String, requestToken: String): ApplicationRequestStatus =
    sendRedirect(authenticateUrl.appendUrlParameters("${HttpAuthHeader.Parameters.OAuthToken}=${requestToken.encodeURL()}"))

private fun ApplicationResponse.redirectAuthenticateOAuth2(settings: OAuthServerSettings.OAuth2ServerSettings, callbackRedirectUrl: String, state: String, extraParameters: List<Pair<String, String>> = emptyList(), scopes: List<String> = emptyList()): ApplicationRequestStatus {
    return redirectAuthenticateOAuth2(authenticateUrl = settings.authorizeUrl,
            callbackRedirectUrl = callbackRedirectUrl,
            clientId = settings.clientId,
            state = state,
            scopes = scopes,
            parameters = extraParameters)
}

private fun ApplicationResponse.redirectAuthenticateOAuth2(authenticateUrl: String, callbackRedirectUrl: String, clientId: String, state: String, scopes: List<String> = emptyList(), parameters: List<Pair<String, String>> = emptyList()): ApplicationRequestStatus {
    return sendRedirect(authenticateUrl
            .appendUrlParameters("client_id=${clientId.encodeURL()}&redirect_uri=${callbackRedirectUrl.encodeURL()}")
            .appendUrlParameters(optionalParameter("scope", scopes.joinToString(",")))
            .appendUrlParameters("state=${state.encodeURL()}")
            .appendUrlParameters("response_type=code")
            .appendUrlParameters(parameters.formUrlEncode())
    )
}

private fun simpleOAuth1aStep1(settings: OAuthServerSettings.OAuth1aServerSettings, callbackUrl: String, nonce: String = nextNonce(), extraParameters: List<Pair<String, String>> = emptyList()): OAuthCallback.TokenPair {
    return simpleOAuth1aStep1(
            settings.consumerSecret + "&",
            settings.requestTokenUrl,
            callbackUrl,
            settings.consumerKey,
            nonce,
            extraParameters
    )
}

private fun simpleOAuth1aStep1(secretKey: String, baseUrl: String, callback: String, consumerKey: String, nonce: String = nextNonce(), extraParameters: List<Pair<String, String>> = emptyList()): OAuthCallback.TokenPair {
    val authHeader = obtainRequestTokenHeader(
            callback = callback,
            consumerKey = consumerKey,
            nonce = nonce
    ).sign(HttpMethod.Post, baseUrl, secretKey, extraParameters)

    val connection = URL(baseUrl.appendUrlParameters(extraParameters.formUrlEncode())).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.instanceFollowRedirects = false
    connection.setRequestProperty(HttpHeaders.Authorization, authHeader.render(HeaderValueEncoding.URI_ENCODE))
    connection.setRequestProperty(HttpHeaders.Accept, ContentType.Any.toString())
    connection.doInput = true
    connection.doOutput = true

    try {
        connection.outputStream.close()
        val response = connection.inputStream.reader().readText().parseUrlEncodedParameters()
        require(response["oauth_callback_confirmed"] == "true") { "Response parameter oauth_callback_confirmed should be true" }

        return OAuthCallback.TokenPair(response[HttpAuthHeader.Parameters.OAuthToken]!!, response[HttpAuthHeader.Parameters.OAuthTokenSecret]!!)
    } catch (e: Throwable) {
        throw IOException("Failed to acquire request token due to ${connection.errorStream?.reader()?.readText()}", e)
    } finally {
        connection.disconnect()
    }
}

private fun simpleOAuth1aStep2(settings: OAuthServerSettings.OAuth1aServerSettings, callbackResponse: OAuthCallback.TokenPair, nonce: String = nextNonce(), extraParameters: Map<String, String> = emptyMap()): OAuthAccessTokenResponse.OAuth1a {
    return simpleOAuth1aStep2(
            settings.consumerSecret + "&", // TODO??
            settings.accessTokenUrl,
            settings.consumerKey,
            token = callbackResponse.token,
            verifier = callbackResponse.tokenSecret,
            nonce = nonce,
            extraParameters = extraParameters
    )
}

private fun simpleOAuth1aStep2(secretKey: String, baseUrl: String, consumerKey: String, token: String, verifier: String, nonce: String = nextNonce(), extraParameters: Map<String, String> = emptyMap()): OAuthAccessTokenResponse.OAuth1a {
    val params = listOf(
            HttpAuthHeader.Parameters.OAuthVerifier to verifier
    ) + extraParameters.toList()
    val authHeader = upgradeRequestTokenHeader(consumerKey, token, nonce).sign(HttpMethod.Post, baseUrl, secretKey, params)

    val connection = URL(baseUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.instanceFollowRedirects = false
    connection.setRequestProperty(HttpHeaders.Authorization, authHeader.render(HeaderValueEncoding.URI_ENCODE))
    connection.setRequestProperty(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
    connection.setRequestProperty(HttpHeaders.Accept, "*/*")
    connection.doInput = true
    connection.doOutput = true

    try {
        connection.outputStream.buffered().writer().use { writer ->
            params.formUrlEncodeTo(writer)
        }

        val response = connection.inputStream.reader().readText().parseUrlEncodedParameters()
        return OAuthAccessTokenResponse.OAuth1a(response[HttpAuthHeader.Parameters.OAuthToken]!!, response[HttpAuthHeader.Parameters.OAuthTokenSecret]!!, response)
    } catch (e: Throwable) {
        throw IOException("Failed to acquire request token due to ${connection.errorStream?.reader()?.readText()}", e)
    } finally {
        connection.disconnect()
    }
}

private fun ApplicationRequestContext.oauth1aHandleCallback(): OAuthCallback.TokenPair? {
    val token = request.parameter(HttpAuthHeader.Parameters.OAuthToken)
    val verifier = request.parameter(HttpAuthHeader.Parameters.OAuthVerifier)

    return when {
        token != null && verifier != null -> OAuthCallback.TokenPair(token, verifier)
        else -> null
    }
}

private fun ApplicationRequestContext.oauth2HandleCallback(): OAuthCallback.TokenSingle? {
    val code = request.parameter("code")
    val state = request.parameter("state")

    return when {
        code != null && state != null -> OAuthCallback.TokenSingle(code, state)
        else -> null
    }
}

private fun simpleOAuth2Step2(settings: OAuthServerSettings.OAuth2ServerSettings, usedRedirectUrl: String, callbackResponse: OAuthCallback.TokenSingle, extraParameters: Map<String, String> = emptyMap(), configure: HttpURLConnection.() -> Unit = {}): OAuthAccessTokenResponse.OAuth2 {
    return simpleOAuth2Step2(
            settings.requestMethod,
            usedRedirectUrl,
            settings.accessTokenUrl,
            settings.clientId,
            settings.clientSecret,
            callbackResponse.state,
            callbackResponse.token,
            extraParameters,
            configure
    )
}

private fun simpleOAuth2Step2(method: HttpMethod, usedRedirectUrl: String, baseUrl: String, clientId: String, clientSecret: String, state: String, code: String, extraParameters: Map<String, String> = emptyMap(), configure: HttpURLConnection.() -> Unit = {}): OAuthAccessTokenResponse.OAuth2 {
    val urlParameters =
            (listOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "grant_type" to "authorization_code",
                    "state" to state,
                    "code" to code,
                    "redirect_uri" to usedRedirectUrl
            ) + extraParameters.toList()).formUrlEncode()

    val getUri = when (method) {
        HttpMethod.Get -> baseUrl.appendUrlParameters(urlParameters)
        HttpMethod.Post -> baseUrl
        else -> throw UnsupportedOperationException()
    }

    val connection = URL(getUri).openConnection() as HttpURLConnection
    connection.requestMethod = method.value.toUpperCase()
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.instanceFollowRedirects = false
    connection.setRequestProperty(HttpHeaders.Accept, listOf(ContentType.Application.FormUrlEncoded, ContentType.Application.Json).joinToString(","))

    try {
        connection.configure()
        if (method == HttpMethod.Post) {
            connection.setRequestProperty(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            connection.doOutput = true

            connection.outputStream.bufferedWriter().use { out ->
                out.write(urlParameters)
            }
        }

        val contentType = ContentType.parse(connection.getHeaderField("Content-Type"))
        val content = connection.inputStream.bufferedReader().readText()

        val contentDecoded = decodeContent(content, contentType)

        if (contentDecoded.contains("error")) {
            throw IOException("OAuth server respond with error: $contentDecoded")
        }

        return OAuthAccessTokenResponse.OAuth2(
                accessToken = contentDecoded["access_token"]!!,
                tokenType = contentDecoded["token_type"] ?: "",
                expiresIn = contentDecoded["expires_in"]?.toLong() ?: 0L,
                refreshToken = contentDecoded["refresh_token"],
                extraParameters = contentDecoded
        )
    } catch (t: Throwable) {
        throw IOException("Failed to acquire request token due to ${connection.errorStream?.reader()?.readText()}", t)
    } finally {
        connection.disconnect()
    }
}

fun ApplicationRequestContext.simpleOAuthAnyStep1(exec: ExecutorService, provider: OAuthServerSettings, callbackUrl: String, loginPageUrl: String): ApplicationRequestStatus =
    when (provider) {
        is OAuthServerSettings.OAuth1aServerSettings -> {
            handleAsync(exec, {
                val requestToken = simpleOAuth1aStep1(provider, callbackUrl)
                response.redirectAuthenticateOAuth1a(provider, requestToken)
            }, failBlock = oauthHandleFail(loginPageUrl))
        }
        is OAuthServerSettings.OAuth2ServerSettings ->
            response.redirectAuthenticateOAuth2(provider, callbackUrl, nextNonce(), scopes = provider.defaultScopes)
    }

fun ApplicationRequestContext.simpleOAuthAnyStep2(exec: ExecutorService, provider: OAuthServerSettings, callbackUrl: String, loginPageUrl: String, block: (OAuthAccessTokenResponse) -> ApplicationRequestStatus): ApplicationRequestStatus =
        when (provider) {
            is OAuthServerSettings.OAuth1aServerSettings -> {
                val tokens = oauth1aHandleCallback()
                if (tokens == null) {
                    response.sendRedirect(loginPageUrl)
                    ApplicationRequestStatus.Handled
                } else {
                    handleAsync(exec, {
                        val accessToken = simpleOAuth1aStep2(provider, tokens)
                        block(accessToken)
                    }, failBlock = oauthHandleFail(loginPageUrl))
                }
            }
            is OAuthServerSettings.OAuth2ServerSettings -> {
                val code = oauth2HandleCallback()
                if (code == null) {
                    response.sendRedirect(loginPageUrl)
                    ApplicationRequestStatus.Handled
                } else {
                    // TODO: here we should verify code.state but we omit it for demo purposes
                    handleAsync(exec, {
                        val accessToken = simpleOAuth2Step2(
                                provider,
                                callbackUrl,
                                code
                        )

                        block(accessToken)
                    }, oauthHandleFail(loginPageUrl))
                }
            }
        }

private fun <C: ApplicationRequestContext> AuthBuilder<C>.oauth1a(exec: ExecutorService,
                         providerLookup: C.() -> OAuthServerSettings?,
                         urlProvider: C.(OAuthServerSettings) -> String) {
    intercept { next ->
        val provider = providerLookup()
        when (provider) {
            is OAuthServerSettings.OAuth1aServerSettings -> {
                val token = oauth1aHandleCallback()
                    handleAsync(exec, {
                        if (token == null) {
                            val t = simpleOAuth1aStep1(provider, urlProvider(provider))
                            response.redirectAuthenticateOAuth1a(provider, t)
                        } else {
                            val accessToken = simpleOAuth1aStep2(provider, token)
                            AuthContext.from(this).addPrincipal(accessToken)
                            next()
                        }
                    }, failBlock = {
                        next()
                    })
            }
            else -> next()
        }
    }
}

private fun <C: ApplicationRequestContext> AuthBuilder<C>.oauth2(exec: ExecutorService,
                        providerLookup: C.() -> OAuthServerSettings?,
                        urlProvider: C.(OAuthServerSettings) -> String) {
    intercept { next ->
        val provider = providerLookup()
        when (provider) {
            is OAuthServerSettings.OAuth2ServerSettings -> {
                val token = oauth2HandleCallback()
                if (token == null) {
                    response.redirectAuthenticateOAuth2(provider, urlProvider(provider), nextNonce(), scopes = provider.defaultScopes)
                } else {
                    handleAsync(exec, {
                        val accessToken = simpleOAuth2Step2(provider, urlProvider(provider), token)
                        AuthContext.from(this).addPrincipal(accessToken)
                        next()
                    }, failBlock = {
                        next()
                    })
                }
            }
            else -> next()
        }
    }
}

fun <C: ApplicationRequestContext> AuthBuilder<C>.oauth(exec: ExecutorService,
                       providerLookup: C.() -> OAuthServerSettings?,
                       urlProvider: C.(OAuthServerSettings) -> String) {
    oauth1a(exec, providerLookup, urlProvider)
    oauth2(exec, providerLookup, urlProvider)
}

inline fun <reified T: Any> AuthBuilder<RoutingApplicationRequestContext>.oauthAtLocation(exec: ExecutorService,
                                                         noinline providerLookup: RoutingApplicationRequestContext.(T) -> OAuthServerSettings?,
                                                         noinline urlProvider: RoutingApplicationRequestContext.(T, OAuthServerSettings) -> String) {
    oauthWithType(T::class, exec, providerLookup, urlProvider)
}

fun <T: Any> AuthBuilder<RoutingApplicationRequestContext>.oauthWithType(type: KClass<T>,
                                        exec: ExecutorService,
                                        providerLookup: RoutingApplicationRequestContext.(T) -> OAuthServerSettings?,
                                        urlProvider: RoutingApplicationRequestContext.(T, OAuthServerSettings) -> String) {

    fun RoutingApplicationRequestContext.resolve(): T {
        val locationService = resolveResult.entry.getService(locationServiceKey)
        return locationService.resolve<T>(type, this)
    }
    fun RoutingApplicationRequestContext.providerLookupLocal(): OAuthServerSettings? = providerLookup(resolve())
    fun RoutingApplicationRequestContext.urlProviderLocal(s: OAuthServerSettings): String = urlProvider(resolve(), s)

    oauth(exec,
            RoutingApplicationRequestContext::providerLookupLocal,
            RoutingApplicationRequestContext::urlProviderLocal)
}

private fun ApplicationRequestContext.oauthHandleFail(redirectUrl: String) = { t: Throwable ->
    application.config.log.error(t)
    response.sendRedirect(redirectUrl)
    Unit
}

private fun decodeContent(content: String, contentType: ContentType): ValuesMap = when {
    contentType.match(ContentType.Application.FormUrlEncoded) -> content.parseUrlEncodedParameters()
    contentType.match(ContentType.Application.Json) ->
        (JSONValue.parseWithException(content) as JSONObject)
                .toList()
                .fold(ValuesMap.Builder()) { builder, e -> builder.append(e.first.toString(), e.second.toString()); builder }.build() // TODO better json handling
// TODO text/xml
    else -> {
        // some servers may respond with wrong content type so we have to try to guess
        when {
            content.startsWith("{") && content.trim().endsWith("}") -> decodeContent(content.trim(), ContentType.Application.Json)
            content.matches("([a-zA-Z\\d_-]+=[^=&]+&?)+".toRegex()) -> decodeContent(content, ContentType.Application.FormUrlEncoded) // TODO too risky, isn't it?
            else -> throw IOException("unsupported content type $contentType")
        }
    }
}

private val nonceRandom by lazy { Random(SecureRandom().nextLong()).apply {
    repeat((System.currentTimeMillis() % 17).toInt()) {
        nextGaussian()
    }
} }

private fun nextNonce(): String =
        java.lang.Long.toHexString(nonceRandom.nextLong()) +
                java.lang.Long.toHexString(nonceRandom.nextLong()) +
                java.lang.Long.toHexString(nonceRandom.nextLong()) +
                java.lang.Long.toHexString(nonceRandom.nextLong())

private fun String.appendUrlParameters(parameters: String) =
        when {
            parameters.isEmpty() -> ""
            this.endsWith("?") -> ""
            "?" in this -> "&"
            else -> "?"
        }.let { separator -> "$this$separator$parameters" }

private fun optionalParameter(name: String, value: String, condition: (String) -> Boolean = { it.isNotBlank() }): String =
        if (condition(value)) "${name.encodeURL()}=${value.encodeURL()}"
        else ""

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

fun HttpAuthHeader.Parameterized.sign(method: HttpMethod, baseUrl: String, key: String, parameters: List<Pair<String, String>>) =
    withParameter(HttpAuthHeader.Parameters.OAuthSignature, signatureBaseString(this, method, baseUrl, parameters.toHeaderParamsList()).hmacSha1(key))


private fun String.percentEncode() = encodeURL().replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

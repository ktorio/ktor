/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import org.json.simple.*
import org.slf4j.*
import java.io.*
import java.net.*

private val Logger: Logger = LoggerFactory.getLogger("io.ktor.auth.oauth")

internal suspend fun PipelineContext<Unit, ApplicationCall>.oauth2(
    client: HttpClient, dispatcher: CoroutineDispatcher,
    providerLookup: ApplicationCall.() -> OAuthServerSettings?,
    urlProvider: ApplicationCall.(OAuthServerSettings) -> String
) {
    val provider = call.providerLookup()
    if (provider is OAuthServerSettings.OAuth2ServerSettings) {
        val token = call.oauth2HandleCallback()
        val callbackRedirectUrl = call.urlProvider(provider)
        if (token == null) {
            call.redirectAuthenticateOAuth2(
                provider, callbackRedirectUrl,
                state = provider.nonceManager.newNonce(),
                scopes = provider.defaultScopes,
                interceptor = provider.authorizeUrlInterceptor
            )
        } else {
            withContext(dispatcher) {
                try {
                    val accessToken = oauth2RequestAccessToken(client, provider, callbackRedirectUrl, token)
                    call.authentication.principal(accessToken)
                } catch (cause: OAuth2Exception.InvalidGrant) {
                    Logger.trace("Redirected to OAuth2 server due to error invalid_grant: {}", cause.message)
                    call.redirectAuthenticateOAuth2(
                        provider, callbackRedirectUrl,
                        state = provider.nonceManager.newNonce(),
                        scopes = provider.defaultScopes,
                        interceptor = provider.authorizeUrlInterceptor
                    )
                }
            }
        }
    }
}

internal fun ApplicationCall.oauth2HandleCallback(): OAuthCallback.TokenSingle? {
    val code = parameters[OAuth2RequestParameters.Code]
    val state = parameters[OAuth2RequestParameters.State]

    return when {
        code != null && state != null -> OAuthCallback.TokenSingle(code, state)
        else -> null
    }
}

internal suspend fun ApplicationCall.redirectAuthenticateOAuth2(
    settings: OAuthServerSettings.OAuth2ServerSettings,
    callbackRedirectUrl: String,
    state: String,
    extraParameters: List<Pair<String, String>> = emptyList(),
    scopes: List<String> = emptyList(),
    interceptor: URLBuilder.() -> Unit
) {
    redirectAuthenticateOAuth2(
        authenticateUrl = settings.authorizeUrl,
        callbackRedirectUrl = callbackRedirectUrl,
        clientId = settings.clientId,
        state = state,
        scopes = scopes,
        parameters = extraParameters,
        interceptor = interceptor
    )
}

internal suspend fun oauth2RequestAccessToken(
    client: HttpClient,
    settings: OAuthServerSettings.OAuth2ServerSettings,
    usedRedirectUrl: String,
    callbackResponse: OAuthCallback.TokenSingle,
    extraParameters: Map<String, String> = emptyMap(),
    configure: HttpRequestBuilder.() -> Unit = {}
): OAuthAccessTokenResponse.OAuth2 {
    return oauth2RequestAccessToken(
        client,
        settings.requestMethod,
        usedRedirectUrl,
        settings.accessTokenUrl,
        settings.clientId,
        settings.clientSecret,
        callbackResponse.state,
        callbackResponse.token,
        extraParameters,
        configure,
        settings.accessTokenRequiresBasicAuth,
        settings.nonceManager
    )
}

private suspend fun ApplicationCall.redirectAuthenticateOAuth2(
    authenticateUrl: String,
    callbackRedirectUrl: String,
    clientId: String,
    state: String,
    scopes: List<String> = emptyList(),
    parameters: List<Pair<String, String>> = emptyList(),
    interceptor: URLBuilder.() -> Unit = {}
) {

    val url = URLBuilder()
    url.takeFrom(URI(authenticateUrl))
    url.parameters.apply {
        append(OAuth2RequestParameters.ClientId, clientId)
        append(OAuth2RequestParameters.RedirectUri, callbackRedirectUrl)
        if (scopes.isNotEmpty()) {
            append(OAuth2RequestParameters.Scope, scopes.joinToString(" "))
        }
        append(OAuth2RequestParameters.State, state)
        append(OAuth2RequestParameters.ResponseType, "code")
        parameters.forEach { (k, v) ->
            append(k, v)
        }
    }
    interceptor(url)

    return respondRedirect(url.buildString())
}

private suspend fun oauth2RequestAccessToken(
    client: HttpClient,
    method: HttpMethod,
    usedRedirectUrl: String?,
    baseUrl: String,
    clientId: String,
    clientSecret: String,
    state: String?,
    code: String?,
    extraParameters: Map<String, String> = emptyMap(),
    configure: HttpRequestBuilder.() -> Unit = {},
    useBasicAuth: Boolean = false,
    nonceManager: NonceManager,
    grantType: String = OAuthGrantTypes.AuthorizationCode
): OAuthAccessTokenResponse.OAuth2 {

    if (state != null) {
        nonceManager.verifyNonce(state)
    }

    val request = HttpRequestBuilder()
    request.url.takeFrom(URI(baseUrl))

    val urlParameters = ParametersBuilder().apply {
        append(OAuth2RequestParameters.ClientId, clientId)
        append(OAuth2RequestParameters.ClientSecret, clientSecret)
        append(OAuth2RequestParameters.GrantType, grantType)
        if (state != null) {
            append(OAuth2RequestParameters.State, state)
        }
        if (code != null) {
            append(OAuth2RequestParameters.Code, code)
        }
        if (usedRedirectUrl != null) {
            append(OAuth2RequestParameters.RedirectUri, usedRedirectUrl)
        }
        extraParameters.forEach { (k, v) ->
            append(k, v)
        }
    }

    when (method) {
        HttpMethod.Get -> request.url.parameters.appendAll(urlParameters)
        HttpMethod.Post -> request.body =
            TextContent(urlParameters.build().formUrlEncode(), ContentType.Application.FormUrlEncoded)
        else -> throw UnsupportedOperationException("Method $method is not supported. Use GET or POST")
    }

    request.apply {
        this.method = method
        header(
            HttpHeaders.Accept,
            listOf(ContentType.Application.FormUrlEncoded, ContentType.Application.Json).joinToString(",")
        )
        if (useBasicAuth) {
            header(
                HttpHeaders.Authorization,
                HttpAuthHeader.Single(
                    AuthScheme.Basic,
                    "$clientId:$clientSecret".toByteArray(Charsets.ISO_8859_1).encodeBase64()
                ).render()
            )
        }

        configure()
    }

    val response = client.request<HttpResponse>(request)

    val body = response.readText()

    val (contentType, content) = try {
        if (response.status == HttpStatusCode.NotFound) {
            throw IOException("Access token query failed with http status 404 for the page $baseUrl")
        }
        val contentType = response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) } ?: ContentType.Any

        Pair(contentType, body)
    } catch (ioe: IOException) {
        throw ioe
    } catch (t: Throwable) {
        throw IOException("Failed to acquire request token due to wrong content: $body", t)
    }

    val contentDecodeResult = Result.runCatching { decodeContent(content, contentType) }
    val errorCode = contentDecodeResult.map { it[OAuth2ResponseParameters.Error] }

    // try error code first
    errorCode.getOrNull()?.let {
        throwOAuthError(it, contentDecodeResult.getOrThrow())
    }

    // ensure status code is successful
    if (!response.status.isSuccess()) {
        throw IOException("Access token query failed with http status ${response.status} for the page $baseUrl")
    }

    // will fail if content decode failed but status is OK
    val contentDecoded = contentDecodeResult.getOrThrow()

    // finally extract an access token
    return OAuthAccessTokenResponse.OAuth2(
        accessToken = contentDecoded[OAuth2ResponseParameters.AccessToken]
            ?: throw OAuth2Exception.MissingAccessToken(),
        tokenType = contentDecoded[OAuth2ResponseParameters.TokenType] ?: "",
        expiresIn = contentDecoded[OAuth2ResponseParameters.ExpiresIn]?.toLong() ?: 0L,
        refreshToken = contentDecoded[OAuth2ResponseParameters.RefreshToken],
        extraParameters = contentDecoded
    )
}

private fun decodeContent(content: String, contentType: ContentType): Parameters = when {
    contentType.match(ContentType.Application.FormUrlEncoded) -> content.parseUrlEncodedParameters()
    contentType.match(ContentType.Application.Json) -> Parameters.build {
        (JSONValue.parseWithException(content) as JSONObject).forEach {
            append(it.key.toString(), it.value.toString())
        }
    } // TODO better json handling
// TODO text/xml
    else -> {
        // some servers may respond with wrong content type so we have to try to guess
        when {
            content.startsWith("{") && content.trim().endsWith("}") -> decodeContent(
                content.trim(),
                ContentType.Application.Json
            )
            content.matches("([a-zA-Z\\d_-]+=[^=&]+&?)+".toRegex()) -> decodeContent(
                content,
                ContentType.Application.FormUrlEncoded
            ) // TODO too risky, isn't it?
            else -> throw IOException("unsupported content type $contentType")
        }
    }
}

/**
 * Implements Resource Owner Password Credentials Grant
 * see http://tools.ietf.org/html/rfc6749#section-4.3
 *
 * Takes [UserPasswordCredential] and validates it using OAuth2 sequence, provides [OAuthAccessTokenResponse.OAuth2] if succeeds
 */
suspend fun verifyWithOAuth2(
    credential: UserPasswordCredential,
    client: HttpClient,
    settings: OAuthServerSettings.OAuth2ServerSettings
): OAuthAccessTokenResponse.OAuth2 {
    return oauth2RequestAccessToken(
        client, HttpMethod.Post,
        usedRedirectUrl = null,
        baseUrl = settings.accessTokenUrl,
        clientId = settings.clientId,
        clientSecret = settings.clientSecret,
        code = null,
        state = null,
        configure = {},
        extraParameters = mapOf(
            OAuth2RequestParameters.UserName to credential.name,
            OAuth2RequestParameters.Password to credential.password
        ),
        useBasicAuth = true,
        nonceManager = settings.nonceManager,
        grantType = OAuthGrantTypes.Password
    )
}

/**
 * List of OAuth2 request parameters for both peers
 */
@Suppress("KDocMissingDocumentation")
object OAuth2RequestParameters {
    const val ClientId = "client_id"
    const val Scope = "scope"
    const val ClientSecret = "client_secret"
    const val GrantType = "grant_type"
    const val Code = "code"
    const val State = "state"
    const val RedirectUri = "redirect_uri"
    const val ResponseType = "response_type"
    const val UserName = "username"
    const val Password = "password"
}

/**
 * List of OAuth2 server response parameters
 */
@Suppress("KDocMissingDocumentation")
object OAuth2ResponseParameters {
    const val AccessToken = "access_token"
    const val TokenType = "token_type"
    const val ExpiresIn = "expires_in"
    const val RefreshToken = "refresh_token"
    const val Error = "error"
    const val ErrorDescription = "error_description"
}

private fun throwOAuthError(errorCode: String, parameters: Parameters): Nothing {
    val errorDescription = parameters.get("error_description") ?: "OAuth2 Server responded with $errorCode"

    throw when (errorCode) {
        "invalid_grant" -> OAuth2Exception.InvalidGrant(errorDescription)
        else -> OAuth2Exception.UnknownException(errorDescription, errorCode)
    }
}

/**
 * Represents a error during communicating to OAuth2 server
 * @property errorCode OAuth2 server replied with
 */
@KtorExperimentalAPI
sealed class OAuth2Exception(message: String, val errorCode: String?) : Exception(message) {
    /**
     * OAuth2 server responded error="invalid_grant"
     */
    @KtorExperimentalAPI
    class InvalidGrant(message: String) : OAuth2Exception(message, "invalid_grant")

    /**
     * Thrown when an OAuth2 server replied with successful HTTP status and expected content type that was successfully
     * decoded but the response doesn't contain error code nor access token
     */
    @KtorExperimentalAPI
    class MissingAccessToken : OAuth2Exception(
        "OAuth2 server response is OK neither error nor access token provided", null
    )

    /**
     * Throw when an OAuth2 server replied with error "unsupported_grant_type"
     * @param grantType that was passed to the server
     */
    @KtorExperimentalAPI
    class UnsupportedGrantType(val grantType: String) : OAuth2Exception(
        "OAuth2 server doesn't support grant type $grantType", "unsupported_grant_type"
    ), CopyableThrowable<UnsupportedGrantType> {
        override fun createCopy(): UnsupportedGrantType = UnsupportedGrantType(grantType).also {
            it.initCause(this)
        }
    }

    /**
     * OAuth2 server responded with an error code [errorCode]
     * @param errorCode the OAuth2 server replied with
     */
    @KtorExperimentalAPI
    class UnknownException(
        private val details: String, errorCode: String
    ) : OAuth2Exception("$details (error code = $errorCode)", errorCode), CopyableThrowable<UnknownException> {
        override fun createCopy(): UnknownException = UnknownException(details, errorCode!!).also {
            it.initCause(this)
        }
    }
}

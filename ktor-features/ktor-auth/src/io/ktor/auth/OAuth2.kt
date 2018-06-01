package io.ktor.auth

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import org.json.simple.*
import java.io.*
import java.net.*

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
            val stateProvider = provider.stateProvider
            call.redirectAuthenticateOAuth2(provider, callbackRedirectUrl,
                    state = stateProvider.getState(call),
                    scopes = provider.defaultScopes,
                    interceptor = provider.authorizeUrlInterceptor)
        } else {
            withContext(dispatcher) {
                val accessToken = simpleOAuth2Step2(client, provider, callbackRedirectUrl, token)
                call.authentication.principal(accessToken)
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

internal suspend fun ApplicationCall.redirectAuthenticateOAuth2(settings: OAuthServerSettings.OAuth2ServerSettings, callbackRedirectUrl: String, state: String, extraParameters: List<Pair<String, String>> = emptyList(), scopes: List<String> = emptyList(), interceptor: URLBuilder.() -> Unit) {
    redirectAuthenticateOAuth2(authenticateUrl = settings.authorizeUrl,
            callbackRedirectUrl = callbackRedirectUrl,
            clientId = settings.clientId,
            state = state,
            scopes = scopes,
            parameters = extraParameters,
            interceptor = interceptor)
}

internal suspend fun simpleOAuth2Step2(client: HttpClient,
                                       settings: OAuthServerSettings.OAuth2ServerSettings,
                                       usedRedirectUrl: String,
                                       callbackResponse: OAuthCallback.TokenSingle,
                                       extraParameters: Map<String, String> = emptyMap(),
                                       configure: HttpRequestBuilder.() -> Unit = {}): OAuthAccessTokenResponse.OAuth2 {
    return simpleOAuth2Step2(
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
            settings.stateProvider
    )
}

private suspend fun ApplicationCall.redirectAuthenticateOAuth2(authenticateUrl: String,
                                                               callbackRedirectUrl: String,
                                                               clientId: String,
                                                               state: String,
                                                               scopes: List<String> = emptyList(),
                                                               parameters: List<Pair<String, String>> = emptyList(),
                                                               interceptor: URLBuilder.() -> Unit = {}) {

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

private suspend fun simpleOAuth2Step2(client: HttpClient,
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
                                      stateProvider: OAuth2StateProvider = DefaultOAuth2StateProvider,
                                      grantType: String = OAuthGrantTypes.AuthorizationCode): OAuthAccessTokenResponse.OAuth2 {

    if (state != null) {
        stateProvider.verifyState(state)
    }

    val request = HttpRequestBuilder()
    request.url.takeFrom(URI(baseUrl))

    val urlParameters = ParametersBuilder().apply {
        append(OAuth2RequestParameters.ClientId,  clientId)
        append(OAuth2RequestParameters.ClientSecret,  clientSecret)
        append(OAuth2RequestParameters.GrantType,  grantType)
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
        HttpMethod.Post -> request.body = TextContent(urlParameters.build().formUrlEncode(), ContentType.Application.FormUrlEncoded)
        else -> throw UnsupportedOperationException("Method $method is not supported. Use GET or POST")
    }

    request.apply {
        this.method = method
        header(HttpHeaders.Accept, listOf(ContentType.Application.FormUrlEncoded, ContentType.Application.Json).joinToString(","))
        if (useBasicAuth) {
            header(
                    HttpHeaders.Authorization,
                    HttpAuthHeader.Single(AuthScheme.Basic, encodeBase64("$clientId:$clientSecret".toByteArray(Charsets.ISO_8859_1))).render()
            )
        }

        configure()
    }

    val response = client.call(request).response

    val body = response.readText()

    val (contentType, content) = try {
        if (response.status == HttpStatusCode.NotFound) {
            throw IOException("Not found. Got 404 for the page $baseUrl")
        }
        val contentType = response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) } ?: ContentType.Any

        Pair(contentType, body)
    } catch (ioe: IOException) {
        throw ioe
    } catch (t: Throwable) {
        throw IOException("Failed to acquire request token due to wrong content: $body", t)
    } finally {
        response.close()
    }

    val contentDecoded = decodeContent(content, contentType)

    if (contentDecoded.contains(OAuth2ResponseParameters.Error)) {
        throw IOException("OAuth server responded with error: $contentDecoded")
    }

    return OAuthAccessTokenResponse.OAuth2(
            accessToken = contentDecoded[OAuth2ResponseParameters.AccessToken]!!,
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
            content.startsWith("{") && content.trim().endsWith("}") -> decodeContent(content.trim(), ContentType.Application.Json)
            content.matches("([a-zA-Z\\d_-]+=[^=&]+&?)+".toRegex()) -> decodeContent(content, ContentType.Application.FormUrlEncoded) // TODO too risky, isn't it?
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
suspend fun verifyWithOAuth2(c: UserPasswordCredential, client: HttpClient, settings: OAuthServerSettings.OAuth2ServerSettings): OAuthAccessTokenResponse.OAuth2 {
    return simpleOAuth2Step2(client, HttpMethod.Post,
            usedRedirectUrl = null,
            baseUrl = settings.accessTokenUrl,
            clientId = settings.clientId,
            clientSecret = settings.clientSecret,
            code = null,
            state = null,
            configure = {},
            extraParameters = mapOf(
                    OAuth2RequestParameters.UserName to c.name,
                    OAuth2RequestParameters.Password to c.password
            ),
            useBasicAuth = true,
            stateProvider = settings.stateProvider,
            grantType = OAuthGrantTypes.Password
    )
}

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

object OAuth2ResponseParameters {
    const val AccessToken = "access_token"
    const val TokenType = "token_type"
    const val ExpiresIn = "expires_in"
    const val RefreshToken = "refresh_token"
    const val Error = "error"
    const val ErrorDescription = "error_description"
}

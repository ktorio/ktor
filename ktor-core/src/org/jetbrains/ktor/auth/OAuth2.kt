package org.jetbrains.ktor.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.httpclient.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import org.json.simple.*
import java.io.*
import java.net.*
import java.util.concurrent.*

internal fun PipelineContext<ApplicationCall>.oauth2(client: HttpClient, exec: ExecutorService,
                                                     providerLookup: ApplicationCall.() -> OAuthServerSettings?,
                                                     urlProvider: ApplicationCall.(OAuthServerSettings) -> String) {
    val provider = call.providerLookup()
    when (provider) {
        is OAuthServerSettings.OAuth2ServerSettings -> {
            val token = call.oauth2HandleCallback()
            val callbackRedirectUrl = call.urlProvider(provider)
            if (token == null) {
                pipeline.finish()
                call.redirectAuthenticateOAuth2(provider, callbackRedirectUrl, nextNonce(), scopes = provider.defaultScopes)
            } else {
                proceedAsync(exec) {
                    val accessToken = simpleOAuth2Step2(client, provider, callbackRedirectUrl, token)
                    if (accessToken != null)
                        call.authentication.addPrincipal(accessToken)
                    else
                        call.oauthHandleFail(callbackRedirectUrl)
                }
            }
        }
    }
}

internal fun ApplicationCall.oauth2HandleCallback(): OAuthCallback.TokenSingle? {
    val code = request.parameter(OAuth2RequestParameters.Code)
    val state = request.parameter(OAuth2RequestParameters.State)

    return when {
        code != null && state != null -> OAuthCallback.TokenSingle(code, state)
        else -> null
    }
}

internal fun ApplicationCall.redirectAuthenticateOAuth2(settings: OAuthServerSettings.OAuth2ServerSettings, callbackRedirectUrl: String, state: String, extraParameters: List<Pair<String, String>> = emptyList(), scopes: List<String> = emptyList()) {
    return redirectAuthenticateOAuth2(authenticateUrl = settings.authorizeUrl,
            callbackRedirectUrl = callbackRedirectUrl,
            clientId = settings.clientId,
            state = state,
            scopes = scopes,
            parameters = extraParameters)
}

internal fun simpleOAuth2Step2(client: HttpClient,
                               settings: OAuthServerSettings.OAuth2ServerSettings,
                               usedRedirectUrl: String,
                               callbackResponse: OAuthCallback.TokenSingle,
                               extraParameters: Map<String, String> = emptyMap(),
                               configure: RequestBuilder.() -> Unit = {}): OAuthAccessTokenResponse.OAuth2? {
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
            settings.accessTokenRequiresBasicAuth
    )
}

private fun ApplicationCall.redirectAuthenticateOAuth2(authenticateUrl: String, callbackRedirectUrl: String, clientId: String, state: String, scopes: List<String> = emptyList(), parameters: List<Pair<String, String>> = emptyList()) {
    return respondRedirect(authenticateUrl
            .appendUrlParameters("${OAuth2RequestParameters.ClientId}=${encodeURLQueryComponent(clientId)}&${OAuth2RequestParameters.RedirectUri}=${encodeURLQueryComponent(callbackRedirectUrl)}")
            .appendUrlParameters(optionalParameter(OAuth2RequestParameters.Scope, scopes.joinToString(",")))
            .appendUrlParameters("${OAuth2RequestParameters.State}=${encodeURLQueryComponent(state)}")
            .appendUrlParameters("${OAuth2RequestParameters.ResponseType}=code")
            .appendUrlParameters(parameters.formUrlEncode())
    )
}

internal fun optionalParameter(name: String, value: String, condition: (String) -> Boolean = { it.isNotBlank() }): String =
        if (condition(value)) "${encodeURLQueryComponent(name)}=${encodeURLQueryComponent(value)}"
        else ""

private fun simpleOAuth2Step2(client: HttpClient,
                              method: HttpMethod,
                              usedRedirectUrl: String?,
                              baseUrl: String,
                              clientId: String,
                              clientSecret: String,
                              state: String?,
                              code: String?,
                              extraParameters: Map<String, String> = emptyMap(),
                              configure: RequestBuilder.() -> Unit = {},
                              useBasicAuth: Boolean = false,
                              grantType: String = OAuthGrandTypes.AuthorizationCode): OAuthAccessTokenResponse.OAuth2? {
    val urlParameters =
            (listOf(
                    OAuth2RequestParameters.ClientId to clientId,
                    OAuth2RequestParameters.ClientSecret to clientSecret,
                    OAuth2RequestParameters.GrantType to grantType,
                    OAuth2RequestParameters.State to state,
                    OAuth2RequestParameters.Code to code,
                    OAuth2RequestParameters.RedirectUri to usedRedirectUrl
            ).filterNotNull() + extraParameters.toList()).formUrlEncode()

    val getUri = when (method) {
        HttpMethod.Get -> baseUrl.appendUrlParameters(urlParameters)
        HttpMethod.Post -> baseUrl
        else -> throw UnsupportedOperationException()
    }

    val connection = client.open(URL(getUri)) {
        this.method = method
        header(HttpHeaders.Accept, listOf(ContentType.Application.FormUrlEncoded, ContentType.Application.Json).joinToString(","))
        if (useBasicAuth) {
            header(HttpHeaders.Authorization, HttpAuthHeader.Single(AuthScheme.Basic, encodeBase64("$clientId:$clientSecret".toByteArray(Charsets.ISO_8859_1))).render())
        }
        configure()

        if (method == HttpMethod.Post) {
            contentType(ContentType.Application.FormUrlEncoded)
            body = {
                it.writer().use { out ->
                    out.write(urlParameters)
                }
            }
        }
    }

    try {
        if (connection.responseStatus == HttpStatusCode.NotFound) {
            throw IOException("Not found 404 for the page $baseUrl")
        }
        val contentType = connection.responseHeaders[HttpHeaders.ContentType]?.let { ContentType.parse(it) } ?: ContentType.Any
        val content = connection.responseStream.bufferedReader().readText()

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
    } catch (t: Throwable) {
        return null
        throw IOException("Failed to acquire request token due to ${connection.responseStream.reader().readText()}", t)
    } finally {
        connection.close()
    }
}

private fun decodeContent(content: String, contentType: ContentType): ValuesMap = when {
    contentType.match(ContentType.Application.FormUrlEncoded) -> content.parseUrlEncodedParameters()
    contentType.match(ContentType.Application.Json) ->
        (JSONValue.parseWithException(content) as JSONObject)
                .toList()
                .fold(ValuesMapImpl.Builder()) { builder, e -> builder.append(e.first.toString(), e.second.toString()); builder }.build() // TODO better json handling
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
fun PipelineContext<ApplicationCall>.verifyWithOAuth2(client: HttpClient, settings: OAuthServerSettings.OAuth2ServerSettings) {
    verifyWith { c: UserPasswordCredential ->
        simpleOAuth2Step2(client, HttpMethod.Post,
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
                grantType = OAuthGrandTypes.Password
        )
    }
}

fun verifyWithOAuth2(c: UserPasswordCredential, client: HttpClient, settings: OAuthServerSettings.OAuth2ServerSettings): OAuthAccessTokenResponse.OAuth2? {
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
            grantType = OAuthGrandTypes.Password
    )
}

object OAuth2RequestParameters {
    val ClientId = "client_id"
    val Scope = "scope"
    val ClientSecret = "client_secret"
    val GrantType = "grant_type"
    val Code = "code"
    val State = "state"
    val RedirectUri = "redirect_uri"
    val ResponseType = "response_type"
    val UserName = "username"
    val Password = "password"
}

object OAuth2ResponseParameters {
    val AccessToken = "access_token"
    val TokenType = "token_type"
    val ExpiresIn = "expires_in"
    val RefreshToken = "refresh_token"
    val Error = "error"
    val ErrorDescription = "error_description"
}

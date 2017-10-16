package io.ktor.samples.auth

import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.install
import io.ktor.auth.OAuthAccessTokenResponse
import io.ktor.auth.OAuthServerSettings
import io.ktor.auth.authentication
import io.ktor.client.HttpClient
import io.ktor.client.backend.jvm.ApacheBackend
import io.ktor.features.CallLogging
import io.ktor.features.DefaultHeaders
import io.ktor.html.respondHtml
import io.ktor.http.HttpMethod
import io.ktor.locations.*
import io.ktor.pipeline.call
import io.ktor.request.host
import io.ktor.request.port
import io.ktor.routing.Routing
import io.ktor.routing.param
import kotlinx.html.*
import java.util.concurrent.Executors

@location("/") class index()
@location("/login/{type?}") class login(val type: String = "")

/**
 * DISCLAIMER
 *
 * The constants are only for demo purposes. You should NEVER keep secret keys in the source code
 * but store them safe externally instead.
 * You also can keep them encrypted but in this case you have to keep encryption key safe
 *
 * Also you SHOULD ALWAYS use HTTPS with OAuth2
 */
val loginProviders = listOf(
        OAuthServerSettings.OAuth1aServerSettings(
                name = "twitter",
                requestTokenUrl = "https://api.twitter.com/oauth/request_token",
                authorizeUrl = "https://api.twitter.com/oauth/authorize",
                accessTokenUrl = "https://api.twitter.com/oauth/access_token",

                consumerKey = "***",
                consumerSecret = "***"
        ),
        OAuthServerSettings.OAuth2ServerSettings(
                name = "vk",
                authorizeUrl = "https://oauth.vk.com/authorize",
                accessTokenUrl = "https://oauth.vk.com/access_token",
                clientId = "***",
                clientSecret = "***"
        ),
        OAuthServerSettings.OAuth2ServerSettings(
                name = "github",
                authorizeUrl = "https://github.com/login/oauth/authorize",
                accessTokenUrl = "https://github.com/login/oauth/access_token",
                clientId = "***",
                clientSecret = "***"
        ),
        OAuthServerSettings.OAuth2ServerSettings(
                name = "google",
                authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                accessTokenUrl = "https://www.googleapis.com/oauth2/v3/token",
                requestMethod = HttpMethod.Post,

                clientId = "***.apps.googleusercontent.com",
                clientSecret = "***",
                defaultScopes = listOf("https://www.googleapis.com/auth/plus.login")
        ),
        OAuthServerSettings.OAuth2ServerSettings(
                name = "facebook",
                authorizeUrl = "https://graph.facebook.com/oauth/authorize",
                accessTokenUrl = "https://graph.facebook.com/oauth/access_token",
                requestMethod = HttpMethod.Post,

                clientId = "***",
                clientSecret = "***",
                defaultScopes = listOf("public_profile")
        ),
        OAuthServerSettings.OAuth2ServerSettings(
                name = "hub",
                authorizeUrl = "http://localhost:9099/api/rest/oauth2/auth",
                accessTokenUrl = "http://localhost:9099/api/rest/oauth2/token",
                requestMethod = HttpMethod.Post,

                clientId = "***",
                clientSecret = "***",
                defaultScopes = listOf("***"),
                accessTokenRequiresBasicAuth = true
        )
).associateBy { it.name }

private val exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4)

fun Application.OAuthLoginApplication() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Locations)
    install(Routing) {
        get<index> {
            call.respondHtml {
                head {
                    title { +"index page" }
                }
                body {
                    h1 {
                        +"Try to login"
                    }
                    p {
                        a(href = locations.href(login())) {
                            +"Login"
                        }
                    }
                }
            }
        }

        location<login>() {
            authentication {
                oauthAtLocation<login>(HttpClient(ApacheBackend), exec,
                        providerLookup = { loginProviders[it.type] },
                        urlProvider = { _, p -> redirectUrl(login(p.name), false) })
            }

            param("error") {
                handle {
                    call.loginFailedPage(call.parameters.getAll("error").orEmpty())
                }
            }

            handle {
                val principal = call.authentication.principal<OAuthAccessTokenResponse>()
                if (principal != null) {
                    call.loggedInSuccessResponse(principal)
                } else {
                    call.loginPage()
                }
            }
        }
    }
}

private fun <T : Any> ApplicationCall.redirectUrl(t: T, secure: Boolean = true): String {
    val hostPort = request.host()!! + request.port().let { port -> if (port == 80) "" else ":$port" }
    val protocol = when {
        secure -> "https"
        else -> "http"
    }
    return "$protocol://$hostPort${application.locations.href(t)}"
}

private suspend fun ApplicationCall.loginPage() {
    respondHtml {
        head {
            title { +"Login with" }
        }
        body {
            h1 {
                +"Login with:"
            }

            for (p in loginProviders) {
                p {
                    a(href = application.locations.href(login(p.key))) {
                        +p.key
                    }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.loginFailedPage(errors: List<String>) {
    respondHtml {
        head {
            title { +"Login with" }
        }
        body {
            h1 {
                +"Login error"
            }

            for (e in errors) {
                p {
                    +e
                }
            }
        }
    }
}

private suspend fun ApplicationCall.loggedInSuccessResponse(callback: OAuthAccessTokenResponse) {
    respondHtml {
        head {
            title { +"Logged in" }
        }
        body {
            h1 {
                +"You are logged in"
            }
            p {
                +"Your token is $callback"
            }
        }
    }
}

package org.jetbrains.ktor.samples.auth

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

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

class OAuthLoginApplication : ApplicationFeature<Application, Unit, Unit> {
    override val key = AttributeKey<Unit>(javaClass.simpleName)
    val exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 4)

    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        with(pipeline) {
            install(DefaultHeaders)
            install(CallLogging)
            install(Locations)
            routing {
                get<index>() {
                    call.response.contentType(ContentType.Text.Html)
                    call.respondWrite {
                        appendHTML().html {
                            head {
                                title { +"index page" }
                            }
                            body {
                                h1 {
                                    +"Try to login"
                                }
                                p {
                                    a(href = application.feature(Locations).href(login())) {
                                        +"Login"
                                    }
                                }
                            }
                        }
                    }
                }

                location<login>() {
                    authentication {
                        oauthAtLocation<login>(DefaultHttpClient, exec,
                                providerLookup = { loginProviders[it.type] },
                                urlProvider = { l, p -> redirectUrl(login(p.name), false) })
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
    }

    private fun <T : Any> ApplicationCall.redirectUrl(t: T, secure: Boolean = true): String {
        val hostPort = request.host()!! + request.port().let { port -> if (port == 80) "" else ":$port" }
        val protocol = when {
            secure -> "https"
            else -> "http"
        }
        return "$protocol://$hostPort${application.feature(Locations).href(t)}"
    }

    private fun ApplicationCall.loginPage() {
        response.contentType(ContentType.Text.Html)
        respondWrite {
            appendHTML().html {
                head {
                    title { +"Login with" }
                }
                body {
                    h1 {
                        +"Login with:"
                    }

                    for (p in loginProviders) {
                        p {
                            a(href = application.feature(Locations).href(login(p.key))) {
                                +p.key
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ApplicationCall.loginFailedPage(errors: List<String>) {
        response.contentType(ContentType.Text.Html)
        respondWrite {
            appendHTML().html {
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
    }

    private fun ApplicationCall.loggedInSuccessResponse(callback: OAuthAccessTokenResponse) {
        response.contentType(ContentType.Text.Html)
        respondWrite {
            appendHTML().html {
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
    }
}

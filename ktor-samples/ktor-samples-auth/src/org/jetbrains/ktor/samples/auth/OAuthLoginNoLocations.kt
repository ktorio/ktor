package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.httpclient.*
import org.jetbrains.ktor.http.*
import java.util.concurrent.*

/**
 * This is special example demonstrates ability to use OAuth with no routes and locations.
 * For general-purpose example see [OAuthLoginApplication]
 */
class OAuthLoginNoLocationApplication(environment: ApplicationEnvironment) : Application(environment) {
    val exec = Executors.newFixedThreadPool(8)

    init {
        intercept(ApplicationCallPipeline.Infrastructure) { call ->
            // generally you shouldn't do like that however there are situation when you could need
            // to do everything on lower level

            when (call.request.parameter("authStep")) {
                "1" -> oauthRespondRedirect(DefaultHttpClient, exec, loginProviders.values.first(), "/any?authStep=2", "/")
                "2" -> oauthHandleCallback(DefaultHttpClient, exec, loginProviders.values.first(), "/any?authStep=2", "/") {
                    call.response.status(HttpStatusCode.OK)
                    call.respondText("success")
                }
            }
        }

        intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.response.status(HttpStatusCode.OK)
            call.response.contentType(ContentType.Text.Html.withCharset(Charsets.UTF_8))
            call.respondText("""
                <html>
                    <body>
                        <a href="?authStep=1">login</a>
                    </body>
                </html>
                """.trimIndent())
        }
    }
}
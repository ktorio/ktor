package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

/**
 * This is special example demonstrates ability to use OAuth with no routes and locations.
 * For general-purpose example see [OAuthLoginApplication]
 */
class OAuthLoginNoLocationApplication : ApplicationModule() {
    val exec = Executors.newFixedThreadPool(8)!!

    override fun install(application: Application) {
        with(application) {
            intercept(ApplicationCallPipeline.Infrastructure) { call ->
                // generally you shouldn't do like that however there are situation when you could need
                // to do everything on lower level

                when (call.parameters["authStep"]) {
                    "1" -> oauthRespondRedirect(DefaultHttpClient, exec, loginProviders.values.first(), "/any?authStep=2")
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
}
package io.ktor.samples.auth

import io.ktor.application.Application
import io.ktor.application.ApplicationCallPipeline
import io.ktor.auth.oauthHandleCallback
import io.ktor.auth.oauthRespondRedirect
import io.ktor.client.HttpClient
import io.ktor.client.backend.jvm.ApacheBackend
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.call
import io.ktor.response.respondText
import java.util.concurrent.Executors

/**
 * This is special example demonstrates ability to use OAuth with no routes and locations.
 * For general-purpose example see [OAuthLoginApplication]
 */
private val exec = Executors.newFixedThreadPool(8)!!

fun Application.OAuthLoginNoLocationApplication() {
    intercept(ApplicationCallPipeline.Infrastructure) {
        // generally you shouldn't do like that however there are situation when you could need
        // to do everything on lower level

        when (call.parameters["authStep"]) {
            "1" -> oauthRespondRedirect(HttpClient(ApacheBackend), exec, loginProviders.values.first(), "/any?authStep=2")
            "2" -> oauthHandleCallback(HttpClient(ApacheBackend), exec, loginProviders.values.first(), "/any?authStep=2", "/") {
                call.response.status(HttpStatusCode.OK)
                call.respondText("success")
            }
        }
    }

    intercept(ApplicationCallPipeline.Infrastructure) {
        call.respondText(ContentType.Text.Html, HttpStatusCode.OK) {
            """
            <html>
                <body>
                    <a href="?authStep=1">login</a>
                </body>
            </html>
            """.trimIndent()
        }
    }
}
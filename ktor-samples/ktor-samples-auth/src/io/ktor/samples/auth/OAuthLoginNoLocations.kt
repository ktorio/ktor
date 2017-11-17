package io.ktor.samples.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

/**
 * This is special example demonstrates ability to use OAuth with no routes and locations.
 * For general-purpose example see [OAuthLoginApplication]
 */
private val exec = Executors.newFixedThreadPool(8)!!

fun Application.OAuthLoginNoLocationApplication() {
    intercept(ApplicationCallPipeline.Infrastructure) {
        // generally you shouldn't do like that however there are situation when you could need
        // to do everything on lower level

        val client = HttpClient(Apache)
        environment.monitor.subscribe(ApplicationStopping) {
            client.close()
        }

        when (call.parameters["authStep"]) {
            "1" -> oauthRespondRedirect(client, exec.asCoroutineDispatcher(), loginProviders.values.first(), "/any?authStep=2")
            "2" -> oauthHandleCallback(client, exec.asCoroutineDispatcher(), loginProviders.values.first(), "/any?authStep=2", "/") {
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
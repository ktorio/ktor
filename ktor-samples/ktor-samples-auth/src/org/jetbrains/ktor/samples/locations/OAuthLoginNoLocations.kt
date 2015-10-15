package org.jetbrains.ktor.samples.locations

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.oauth.*
import org.jetbrains.ktor.http.*
import java.util.concurrent.*

/**
 * This is special example demonstrates ability to use OAuth with no routes and locations.
 * For general-purpose example see [OAuthLoginApplication]
 */
class OAuthLoginNoLocationApplication(config: ApplicationConfig) : Application(config) {
    val exec = Executors.newFixedThreadPool(8)

    init {
        intercept { next ->
            // generally you shouldn't do like that however there are situation when you could need
            // to do everything on lower level

            when (request.parameter("authStep")) {
                "1" -> simpleOAuthAnyStep1(exec, loginProviders.values.first(), "/any?authStep=2", "/")
                "2" -> simpleOAuthAnyStep2(exec,loginProviders.values.first(), "/any?authStep=2", "/") {
                    response.status(HttpStatusCode.OK)
                    response.sendText("success")
                }
                else -> next()
            }
        }

        intercept {
            response.status(HttpStatusCode.OK)
            response.contentType(ContentType.Text.Html.withParameter("charset", Charsets.UTF_8.name()))
            response.write {
                write("""
                <html>
                    <body>
                        <a href="?authStep=1">login</a>
                    </body>
                </html>
                """.trimIndent())
            }

            ApplicationRequestStatus.Handled
        }
    }
}
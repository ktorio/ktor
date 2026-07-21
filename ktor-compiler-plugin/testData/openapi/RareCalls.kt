package openapi

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.method
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.installRareCalls() {
    routing {
        route("/summary") {
            /**
             * Try less common route functions.
             */
            method(HttpMethod.Get) {
                handle {
                    call.respondText("Here is your summary", ContentType.Text.Plain)
                }
            }
        }
    }
}
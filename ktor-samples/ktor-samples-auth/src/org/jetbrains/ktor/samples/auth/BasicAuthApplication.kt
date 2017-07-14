package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*

@location("/manual") class Manual
@location("/userTable") class SimpleUserTable

val hashedUserTable = UserHashedTableAuth(table = mapOf(
        "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
))

fun Application.basicAuthApplication() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Locations)
    install(Routing) {
        location<Manual> {
            authentication {
                basicAuthentication("ktor") { credentials ->
                    if (credentials.name == credentials.password) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }

            get {
                call.respondText("Success, ${call.principal<UserIdPrincipal>()?.name}")
            }
        }

        location<SimpleUserTable> {
            authentication {
                basicAuthentication("ktor") { hashedUserTable.authenticate(it) }
            }

            get {
                call.respondText("Success")
            }
        }

        route("/admin/*") {
            authentication {
                basicAuthentication("ktor") { credentials ->
                    if (credentials.name == credentials.password) {
                        UserIdPrincipal(credentials.name)
                    } else {
                        null
                    }
                }
            }

            get("ui") {
                call.respondText("Success")
            }
        }
    }
}

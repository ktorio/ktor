package io.ktor.samples.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*

@Location("/manual")
class Manual

@Location("/userTable")
class SimpleUserTable

val hashedUserTable = UserHashedTableAuth(table = mapOf(
        "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
))

fun Application.basicAuthApplication() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Locations)

    authentication {
        configure("one") {
            basicAuthentication("ktor") { credentials ->
                if (credentials.name == credentials.password) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
        configure("two") {
            basicAuthentication("ktor") { hashedUserTable.authenticate(it) }
        }
        configure("three") {
            basicAuthentication("ktor") { credentials ->
                if (credentials.name == credentials.password) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
    }

    install(Routing) {
        location<Manual> {
            authenticate("one") {
                get {
                    call.respondText("Success, ${call.principal<UserIdPrincipal>()?.name}")
                }
            }
        }

        location<SimpleUserTable> {
            authenticate("two") {
                get {
                    call.respondText("Success")
                }
            }
        }

        route("/admin/*") {
            authenticate("three") {
                get("ui") {
                    call.respondText("Success")
                }
            }
        }
    }
}

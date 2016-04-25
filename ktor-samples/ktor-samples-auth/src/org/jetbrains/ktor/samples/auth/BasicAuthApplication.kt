package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*

@location("/manual") class Manual()
@location("/userTable") class SimpleUserTable()

class BasicAuthApplication(config: ApplicationConfig) : Application(config) {
    val hashedUserTable = UserHashedTableAuth(table = mapOf(
            "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
    ))

    init {
        install(CallLogging)
        install(Locations)
        routing {
            get<Manual>() {
                authentication {
                    basicAuthentication("ktor") { credentials ->
                        if (credentials.name == credentials.password) {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                }

                call.respondText("Success, ${call.principal<UserIdPrincipal>()?.name}")
            }

            get<SimpleUserTable>() {
                authentication {
                    basicAuthentication("ktor") { hashedUserTable.authenticate(it) }
                }

                call.respondText("Success")
            }
        }
    }
}
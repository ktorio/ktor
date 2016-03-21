package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*

@location("/manual") class Manual()
@location("/userTable") class SimpleUserTable()

class BasicAuthApplication(config: ApplicationConfig) : Application(config) {
    val hashedUserTable = UserHashedTableAuth(table = mapOf(
            "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
    ))

    init {
        install(Locations)
        routing {
            authenticate {
                basicAuth()

                onFail {
                    sendAuthenticationRequest(HttpAuthHeader.basicAuthChallenge("ktor"))
                }
            }
            get<Manual>() {
                authenticate {
                    verifyWith { c: UserPasswordCredential ->
                        if (c.name == c.password) {
                            UserIdPrincipal(c.name)
                        } else {
                            null
                        }
                    }
                }

                response.status(HttpStatusCode.OK)
                respondText("Success, ${principals<UserIdPrincipal>().map { it.name }}")
            }
            get<SimpleUserTable>() {
                authenticate {
                    verifyBatchTypedWith(hashedUserTable)
                }

                response.status(HttpStatusCode.OK)
                respondText("Success")
            }
        }
    }
}
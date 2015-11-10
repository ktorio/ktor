package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*

@location("/manual") class Manual()
@location("/userTable") class SimpleUserTable()

class BasicAuthApplication(config: ApplicationConfig) : Application(config) {
    val hashedUserTable = UserHashedTableAuth(table = mapOf(
            "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
    ))

    init {
        locations {
            auth {
                basicAuth()

                fail {
                    response.sendAuthenticationRequest(HttpAuthHeader.basicAuthChallenge("ktor"))
                }
            }
            get<Manual>() {
                auth {
                    verifyBatchTypedWith { c: List<UserPasswordCredential> ->
                        c.filter { it.name == it.password }.map { UserIdPrincipal(it.name) }
                    }
                }

                response.status(HttpStatusCode.OK)
                response.sendText("Success")
            }
            get<SimpleUserTable>() {
                auth {
                    verifyBatchTypedWith(hashedUserTable)
                }

                response.status(HttpStatusCode.OK)
                response.sendText("Success")
            }
        }
    }
}
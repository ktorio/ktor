package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*

@location("/manual") class Manual()
@location("/userTable") class SimpleUserTable()

class BasicAuthApplication(config: ApplicationConfig) : Application(config) {
    val hashedUserTable = SimpleUserHashedTableAuth(table = mapOf(
            "test" to decodeBase64("VltM4nfheqcJSyH887H+4NEOm2tDuKCl83p5axYXlF0=") // sha256 for "test"
    ))

    init {
        locations {
            get<Manual>() {
                basicAuthValidate { userPass ->
                    userPass.name == userPass.password
                }

                response.status(HttpStatusCode.OK)
                response.sendText("Success")
            }
            get<SimpleUserTable>() {
                basicAuthValidate {
                    hashedUserTable.authenticate(it) != null
                }

                response.status(HttpStatusCode.OK)
                response.sendText("Success")
            }
        }
    }
}
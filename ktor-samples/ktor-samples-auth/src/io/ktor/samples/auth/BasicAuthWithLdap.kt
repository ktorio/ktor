package io.ktor.samples.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.ldap.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*

@Location("/files") class Files()

fun Application.basicAuthWithLdap() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Locations)
    install(Routing) {
        location<Files> {
            authentication {
                basicAuthentication("files") { credentials ->
                    ldapAuthenticate(credentials, "ldap://localhost:389", "cn=%s ou=users") {
                        if (it.name == it.password) {
                            UserIdPrincipal(it.name)
                        } else null
                    }

                }
            }

            handle {
                call.response.status(HttpStatusCode.OK)
                call.respondText("""
                Directory listing

                .
                ..
                dir1
                and so on
                """)
            }
        }
    }
}

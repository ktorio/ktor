package io.ktor.samples.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.ldap.*
import io.ktor.features.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*

@Location("/files")
class Files()

fun Application.basicAuthWithLdap() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Locations)
    install(Authentication) {
        basic {
            validate { credentials ->
                ldapAuthenticate(credentials, "ldap://localhost:389", "cn=%s ou=users") {
                    if (it.name == it.password) {
                        UserIdPrincipal(it.name)
                    } else null
                }

            }
        }
    }

    install(Routing) {
        authenticate {
            get<Files> {
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

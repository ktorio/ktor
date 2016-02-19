package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.ldap.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*

@location("/files") class Files()

class BasicAuthWithLdapApplication(config: ApplicationConfig) : Application(config) {
    init {
        install(Locations)
        routing {
            location<Files> {
                authenticate {
                    basicAuth()

                    verifyWithLdapLoginWithUser("ldap://localhost:389", "cn=%s ou=users")
                    verifyBatchTypedWith { credentials: List<UserPasswordCredential> -> credentials.filter { it.name == it.password }.map { UserIdPrincipal(it.name) } }

                    fail {
                        sendAuthenticationRequest(HttpAuthHeader.basicAuthChallenge("files"))
                    }
                }

                handle {
                    response.status(HttpStatusCode.OK)
                    respondText("""
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
}

package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.ldap.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*

@location("/files") class Files()

class BasicAuthWithLdapApplication(config: ApplicationConfig) : Application(config) {
    init {
        locations {
            location<Files> {
                auth {
                    basicAuth()

                    verifyWithLdapLoginWithUser("ldap://localhost:389", "cn=%s ou=users")
                    verifyBatchTypedWith { credentials: List<UserPasswordCredential> -> credentials.filter { it.name == it.password }.map { UserIdPrincipal(it.name) } }

                    fail {
                        response.sendAuthenticationRequest(HttpAuthHeader.basicAuthChallenge("files"))
                    }
                }

                handle {
                    response.status(HttpStatusCode.OK)
                    response.sendText("""
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

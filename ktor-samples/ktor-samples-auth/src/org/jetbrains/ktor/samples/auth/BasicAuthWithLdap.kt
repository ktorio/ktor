package org.jetbrains.ktor.samples.auth

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.auth.ldap.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*

@location("/files") class Files()

class BasicAuthWithLdapApplication : ApplicationFeature<Application, Unit, Unit> {
    override val key = AttributeKey<Unit>(javaClass.simpleName)

    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        with(pipeline) {
            install(DefaultHeaders)
            install(CallLogging)
            install(Locations)
            routing {
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
    }
}

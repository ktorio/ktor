package org.jetbrains.ktor.samples.auth

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*

class FormPostApplication(config: ApplicationConfig) : Application(config) {
    init {
        routing {
            route("/login") {
                auth {
                    formAuth("user", "pass")
                    verifyWith { up: UserPasswordCredential ->
                        when {
                            up.password == "ppp" -> UserIdPrincipal(up.name)
                            else -> null
                        }
                    }

                    success { auth, next ->
                        respondText("Hello, ${auth.principal<UserIdPrincipal>()!!.name}")
                    }

                    fail {
                        respondText(ContentType.Text.Html, createHTML().html {
                            body {
                                form(action = "/login", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                                    p {
                                        +"user:"
                                        textInput(name = "user") {
                                            value = authContext.credentials<UserPasswordCredential>().firstOrNull()?.name ?: ""
                                        }
                                    }

                                    p {
                                        +"password:"
                                        passwordInput(name = "pass")
                                    }

                                    p {
                                        submitInput() { value = "Login" }
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}

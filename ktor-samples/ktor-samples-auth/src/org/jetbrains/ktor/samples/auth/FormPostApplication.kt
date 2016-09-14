package org.jetbrains.ktor.samples.auth

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*

class FormPostApplication : ApplicationModule() {
    override fun install(application: Application) {
        with(application) {
            install(DefaultHeaders)
            install(CallLogging)
            routing {
                route("/login") {
                    authentication {
                        formAuthentication { up: UserPasswordCredential ->
                            when {
                                up.password == "ppp" -> UserIdPrincipal(up.name)
                                else -> null
                            }
                        }
                    }

                    handle {
                        val principal = call.authentication.principal<UserIdPrincipal>()
                        if (principal != null) {
                            call.respondText("Hello, ${principal.name}")
                        } else {
                            val html = createHTML().html {
                                body {
                                    form(action = "/login", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                                        p {
                                            +"user:"
                                            textInput(name = "user") {
                                                value = principal?.name ?: ""
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
                            }
                            call.respondText(ContentType.Text.Html, html)
                        }
                    }
                }
            }
        }
    }
}

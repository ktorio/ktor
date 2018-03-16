package io.ktor.samples.auth

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.html.*
import kotlinx.html.stream.*

fun Application.formPostAuthApplication() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Authentication) {
        form {
            validate { up: UserPasswordCredential ->
                when {
                    up.password == "ppp" -> UserIdPrincipal(up.name)
                    else -> null
                }
            }
        }
    }

    install(Routing) {
        authenticate {
            get("/login") {
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
                    call.respondText(html, ContentType.Text.Html)
                }
            }
        }
    }
}

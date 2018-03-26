package io.ktor.samples.youkube

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.routing.*
import io.ktor.sessions.*
import kotlinx.html.*

fun Route.login() {
    location<Login> {
        authenticate {
            post {
                val principal = call.principal<UserIdPrincipal>()
                call.sessions.set(YouKubeSession(principal!!.name))
                call.respondRedirect(Index())
            }
        }

        method(HttpMethod.Get) {
            handle<Login> {
                call.respondDefaultHtml(emptyList(), CacheControl.Visibility.Public) {
                    h2 { +"Login" }
                    form(call.url(Login()) { parameters.clear() }, classes = "pure-form-stacked", encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                        acceptCharset = "utf-8"

                        label {
                            +"Username: "
                            textInput {
                                name = Login::userName.name
                                value = it.userName
                            }
                        }
                        label {
                            +"Password: "
                            passwordInput {
                                name = Login::password.name
                            }
                        }
                        submitInput(classes = "pure-button pure-button-primary") {
                            value = "Login"
                        }
                    }
                }
            }
        }
    }
}


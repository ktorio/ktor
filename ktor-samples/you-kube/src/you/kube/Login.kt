package you.kube

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*

fun RoutingEntry.login(users: UserHashedTableAuth) {
    location<Login> {
        method(HttpMethod.Post) {
            authentication {
                formAuthentication(Login::userName.name, Login::password.name, challenge = FormAuthChallenge.Redirect { call, c -> call.url(Login(c?.name ?: "")) }) {
                    users.authenticate(it)
                }
            }

            handle {
                call.session(Session(call.principal<UserIdPrincipal>()!!.name))
                call.respondRedirect(Index())
            }
        }

        method(HttpMethod.Get) {
            handle<Login> {
                call.respondDefaultHtml(emptyList(), CacheControlVisibility.PUBLIC) {
                    p { +"Welcome to You Kube" }
                    h2 { +"Login" }
//                    form(url(Login()), encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                    form(call.url(Login()) { parameters.clear() }, encType = FormEncType.applicationXWwwFormUrlEncoded, method = FormMethod.post) {
                        acceptCharset = "utf-8"

                        ul {
                            li {
                                +"Username: "
                                textInput { name = "userName"; value = it.userName }
                            }
                            li {
                                +"Password: "
                                passwordInput { name = "password" }
                            }
                            li {
                                submitInput { +"Login" }
                            }
                        }
                    }
                }
            }
        }
    }
}

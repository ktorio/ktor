package org.jetbrains.ktor.samples.locations

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.components.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*

@Component
public class WelcomePage(routing: Routing) {
    init {
        with(routing) {
            get("/") {
                call.response.status(HttpStatusCode.OK)
                call.response.contentType(ContentType.Text.Html)
                call.response.write {
                    appendHTML().html {
                        head {
                            title { +"Welcome" }
                        }
                        body {
                            h1 {
                                +"Welcome to Component Application"
                            }
                            ul {
                                li { a(href = "/about.html") { +"About" } }
                                li { a(href = "/information.html") { +"Information" } }
                            }
                        }
                    }
                }
                ApplicationCallResult.Handled
            }

        }
    }
}
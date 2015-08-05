package org.jetbrains.ktor.samples.locations

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.components.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*

component public class WelcomePage(routing: Routing) {
    init {
        with(routing) {
            get("/") {
                respond {
                    contentType(ContentType.Text.Html)
                    contentStream {
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
                    send()
                }
            }

        }
    }
}
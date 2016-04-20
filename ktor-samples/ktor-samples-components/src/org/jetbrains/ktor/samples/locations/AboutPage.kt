package org.jetbrains.ktor.samples.locations

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.components.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*

@Component
public class AboutPage(routing: Routing) {
    init {
        with(routing) {
            get("/about.html") {
                call.response.contentType(ContentType.Text.Html)
                call.respondWrite {
                    appendHTML().html {
                        head {
                            title { +"About" }
                        }
                        body {
                            h1 {
                                +"About Component Application"
                            }
                            +"This application sample shows how to use components to compose the application."
                            +"You often don't need to create special Application type, because ComponentApplication"
                            +"will compose it all"
                        }
                    }
                }
            }

        }
    }
}
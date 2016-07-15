package org.jetbrains.ktor.samples.hello

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*

class HelloApplication(environment: ApplicationEnvironment) : Application(environment) {
    init {
        install(DefaultHeaders)
        install(CallLogging)
        install(DefaultHeaders)
        routing {
            get("/") {
                call.response.contentType(ContentType.Text.Html)
                call.respondWrite {
                    appendHTML().html {
                        head {
                            title { +"Hello World." }
                        }
                        body {
                            h1 {
                                +"Hello, World!"
                            }
                            for (index in 0..2000) {
                                div {
                                    +"Line #$index"
                                }
                                flush()
                                Thread.sleep(5)
                            }
                        }
                    }
                }
            }
            get("/bye") {
                call.respondText("Goodbye World!")
            }
        }
    }
}

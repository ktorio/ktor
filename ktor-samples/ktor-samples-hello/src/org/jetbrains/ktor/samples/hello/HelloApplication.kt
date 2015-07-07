package org.jetbrains.ktor.samples.hello

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*

class HelloApplication(config: ApplicationConfig) : Application(config) {
    init {
        routing {
            get("/") {
                respond {
                    contentType(ContentType.Text.Html)
                    contentStream {
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
                    send()
                }
            }
            get("/bye") {
                respond {
                    content("Goodbye World!")
                    send()
                }
            }
        }
    }
}

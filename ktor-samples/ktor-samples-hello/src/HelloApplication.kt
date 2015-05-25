package ktor.samples.hello

import html4k.*
import html4k.stream.appendHTML
import ktor.application.Application
import ktor.application.ApplicationConfig
import ktor.application.ContentType
import ktor.application.contentType
import ktor.routing.get
import ktor.routing.routing

class HelloApplication(config: ApplicationConfig) : Application(config) {
    init {
        routing {
            get("/") {
                response {
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
                response {
                    content("Goodbye World!")
                    send()
                }
            }
        }
    }
}

package io.ktor.samples.html

import kotlinx.html.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.html.*
import io.ktor.pipeline.*
import io.ktor.routing.*

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Routing) {
        get("/") {
            call.respondHtml {
                head {
                    title { +"HTML Application" }
                }
                body {
                    h1 { +"Sample application with HTML builders" }
                    widget {
                        +"Widgets are just functions"
                    }
                }
            }
        }
    }
}

fun FlowContent.widget(body: FlowContent.() -> Unit) {
    div { body() }
}

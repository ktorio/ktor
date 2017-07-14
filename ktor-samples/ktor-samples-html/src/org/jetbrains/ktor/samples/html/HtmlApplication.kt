package org.jetbrains.ktor.samples.html

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.html.*
import org.jetbrains.ktor.routing.*

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

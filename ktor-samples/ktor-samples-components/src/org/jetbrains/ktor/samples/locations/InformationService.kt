package org.jetbrains.ktor.samples.locations

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.components.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*

component public class InformationService(routing: Routing, providers: Iterable<InformationProvider>) {
    init {
        with(routing) {
            get("/information.html") {
                response.contentType(org.jetbrains.ktor.http.ContentType.Text.Html)
                response.write {
                    appendHTML().html {
                        head {
                            title { +"Information" }
                        }
                        body {
                            h1 {
                                +"Information"
                            }
                            ul {
                                for (information in providers.map { it.information() }) {
                                    li {
                                        div { strong { +information.name } }
                                        div { +information.description }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}

interface InformationProvider {
    fun information(): Information
}

data class Information(val name: String, val description: String)

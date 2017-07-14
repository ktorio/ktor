package org.jetbrains.ktor.samples.locations

import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.html.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*
import java.util.*

@location("/") class index()
@location("/number") class number(val value: Int)

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Locations)
    install(Routing) {
        get<index> {
            call.respondHtml {
                head {
                    title { +"Numbers" }
                }
                body {
                    h1 {
                        +"Choose a Number"
                    }
                    ul {
                        val rnd = Random()
                        (0..5).forEach {
                            li {
                                val number = number(rnd.nextInt(1000))
                                a(href = feature(Locations).href(number)) {
                                    +"Number #${number.value}"
                                }
                            }
                        }
                    }
                }
            }
        }

        get<number> { number ->
            call.respondHtml {
                head {
                    title { +"Numbers" }
                }
                body {
                    h1 {
                        +"Number is ${number.value}"
                    }
                }
            }
        }
    }
}

package org.jetbrains.ktor.samples.locations

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import java.util.*

@location("/") class index()
@location("/number") class number(val value: Int)

class LocationsApplication : ApplicationFeature<Application, Unit, Unit> {
    override val key = AttributeKey<Unit>(javaClass.simpleName)

    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        with(pipeline) {
            install(DefaultHeaders)
            install(CallLogging)
            install(Locations)
            routing {
                get<index>() {
                    call.response.contentType(ContentType.Text.Html)
                    call.respondWrite {
                        appendHTML().html {
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
                                            a(href = application.feature(Locations).href(number)) {
                                                +"Number #${number.value}"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                get<number>() { number ->
                    call.response.contentType(ContentType.Text.Html)
                    call.respondWrite {
                        appendHTML().html {
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
        }
    }
}

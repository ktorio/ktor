package org.jetbrains.ktor.samples.locations

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import java.util.*

@location("/") class index()
@location("/number") class number(val value: Int)

class LocationsApplication(config: ApplicationConfig) : Application(config) {
    init {
        locations {
            get<index>() {

                response.status(HttpStatusCode.OK)
                response.contentType(ContentType.Text.Html)
                response.write {
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
                                        a(href = Locations.href(number)) {
                                            +"Number #${number.value}"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ApplicationRequestStatus.Handled
            }

            get<number>() { number ->
                response.status(HttpStatusCode.OK)
                response.contentType(ContentType.Text.Html)
                response.write {
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
                ApplicationRequestStatus.Handled
            }
        }
    }
}

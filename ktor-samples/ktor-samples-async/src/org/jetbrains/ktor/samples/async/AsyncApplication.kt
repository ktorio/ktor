package org.jetbrains.ktor.samples.async

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.jobs.*
import org.jetbrains.ktor.routing.*
import java.util.*
import kotlin.util.*

class AsyncApplication(config: ApplicationConfig) : Application(config) {
    val jobService = ExecutorJobService(config.log)

    init {
        routing {
            get("/") {
                handle {
                    jobService.async("Respond async task") {
                        handleLongCalculation()

                    }
                    ApplicationRequestStatus.Asynchronous
                }
            }
            get("/bye") {
                handle {
                    response.sendText("Goodbye World!")
                }
            }
        }
    }

    private fun RoutingApplicationRequestContext.handleLongCalculation() {
        var number = 0
        val random = Random()
        val time = measureTimeMillis {
            for (index in 0..300) {
                Thread.sleep(10)
                number += random.nextInt(100)
            }
        }

        response.contentType(ContentType.Text.Html)
        response.write {
            appendHTML().html {
                head {
                    title { +"Async World" }
                }
                body {
                    h1 {
                        +"We calculated this after ${time}ms: $number"
                    }
                }
            }
        }
    }
}

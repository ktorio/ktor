package org.jetbrains.ktor.samples.async

import kotlinx.html.*
import kotlinx.html.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import java.util.*

class AsyncApplication : ApplicationFeature<Application, Unit, Unit> {
    override val key = AttributeKey<Unit>("AsyncApplicationExampleApp")

    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        with(pipeline) {
            install(DefaultHeaders)
            install(CallLogging)

            routing {
                get("/{...}") {
                    val start = System.currentTimeMillis()
                    runAsync(executor) {
                        call.handleLongCalculation(start)
                    }
                }
            }
        }
    }

    private fun ApplicationCall.handleLongCalculation(start: Long) {
        val queue = System.currentTimeMillis() - start
        var number = 0
        val random = Random()
        for (index in 0..300) {
            Thread.sleep(10)
            number += random.nextInt(100)
        }

        val time = System.currentTimeMillis() - start

        response.contentType(ContentType.Text.Html)
        respondWrite {
            appendHTML().html {
                head {
                    title { +"Async World" }
                }
                body {
                    h1 {
                        +"We calculated this after ${time}ms (${queue}ms in queue): $number"
                    }
                }
            }
        }
    }
}

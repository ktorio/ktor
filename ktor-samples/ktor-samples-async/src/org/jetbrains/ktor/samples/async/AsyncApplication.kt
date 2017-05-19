package org.jetbrains.ktor.samples.async

import kotlinx.coroutines.experimental.*
import kotlinx.html.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.html.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.routing.*
import java.util.*

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Routing) {
        get("/{...}") {
            val start = System.currentTimeMillis()
            launch(CommonPool) {
                call.handleLongCalculation(start)
            }
        }
    }
}

private suspend fun ApplicationCall.handleLongCalculation(start: Long) {
    val queue = System.currentTimeMillis() - start
    var number = 0
    val random = Random()
    for (index in 0..300) {
        delay(10)
        number += random.nextInt(100)
    }

    val time = System.currentTimeMillis() - start
    respondHtml {
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

package org.jetbrains.ktor.samples.metrics

import com.codahale.metrics.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.metrics.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import java.util.concurrent.*

fun Application.main() {
    install(DefaultHeaders)
    install(Metrics) {
        val reporter = Slf4jReporter.forRegistry(registry)
                .outputTo(log)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(10, TimeUnit.SECONDS);
    }

    install(Routing) {
        get("/") {
            call.respondText("Hello, World!")
        }
        get("/{param}") {
            call.respondText("Hit ${call.parameters["param"]}")
        }
        get("/error") {
            call.respond(HttpStatusCode.ExceptionFailed)
        }
    }
}
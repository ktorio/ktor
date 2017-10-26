package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*

object CIO : ApplicationEngineFactory<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: CIOApplicationEngine.Configuration.() -> Unit): CIOApplicationEngine {
        return CIOApplicationEngine(environment, configure)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val s = embeddedServer(CIO, 9096) {
            routing {
                get("/") {
                    call.respondText("Hello, World!")
                }
            }
        }

        s.start(true)
    }
}
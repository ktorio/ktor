package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import java.lang.Exception

/**
 * An [ApplicationEngineFactory] providing a CIO-based [ApplicationEngine]
 */
object CIO : ApplicationEngineFactory<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
    override fun create(environment: ApplicationEngineEnvironment, configure: CIOApplicationEngine.Configuration.() -> Unit): CIOApplicationEngine {
        return CIOApplicationEngine(environment, configure)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        embeddedServer(CIO, port = 8080) {
            routing {
                get("/") {
                    call.respond("Yo")
                }
                get("/fail") {
                    throw Exception("hehe")
                }
            }
        }.start(true)
    }
}

package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.host.*

object CIO : ApplicationHostFactory<CIOApplicationHost, CIOApplicationHost.Configuration> {
    override fun create(environment: ApplicationHostEnvironment, configure: CIOApplicationHost.Configuration.() -> Unit): CIOApplicationHost {
        return CIOApplicationHost(environment, configure)
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
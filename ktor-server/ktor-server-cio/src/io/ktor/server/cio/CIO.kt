package io.ktor.server.cio

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.host.*

object CIO : ApplicationHostFactory<CoroutinesHttpHost, CoroutinesHttpHost.Configuration> {
    override fun create(environment: ApplicationHostEnvironment, configure: CoroutinesHttpHost.Configuration.() -> Unit): CoroutinesHttpHost {
        return CoroutinesHttpHost(environment, configure)
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
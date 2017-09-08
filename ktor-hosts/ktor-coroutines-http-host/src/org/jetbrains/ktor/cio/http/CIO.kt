package org.jetbrains.ktor.cio.http

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*

object CIO : ApplicationHostFactory<CoroutinesHttpHost> {
    override fun create(environment: ApplicationHostEnvironment): CoroutinesHttpHost {
        return CoroutinesHttpHost(environment)
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
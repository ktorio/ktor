package org.jetbrains.ktor.samples.hello

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*

class HelloApplication : ApplicationFeature<Application, Unit, Unit> {
    override val key = AttributeKey<Unit>(javaClass.simpleName)
    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        with(pipeline) {
            install(DefaultHeaders)
            install(CallLogging)
            install(Routing) {
                get("/") {
                    call.respondText("Hello, World!")
                }
            }
        }
    }
}

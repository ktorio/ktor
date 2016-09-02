package org.jetbrains.ktor.samples.json

import com.google.gson.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*

data class Model(val name: String, val items: List<Item>)
data class Item(val key: String, val value: String)

class JsonApplication : ApplicationFeature<Application, Unit> {
    override val key = AttributeKey<Unit>(javaClass.simpleName)

    /*
             > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1
             {"name":"root","items":[{"key":"A","value":"Apache"},{"key":"B","value":"Bing"}]}

             > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1/item/A
             {"key":"A","value":"Apache"}
         */

    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        with(pipeline) {
            install(DefaultHeaders)
            install(CallLogging)

            intercept(ApplicationCallPipeline.Infrastructure) { call ->
                if (call.request.accept() == "application/json") {
                    call.response.pipeline.intercept(RespondPipeline.Before) {
                        val message = subject.message
                        when (message) {
                            is Item, is Model -> {
                                call.respond(TextContent(ContentType.Application.Json, GsonBuilder().create().toJson(message)))
                            }
                        }
                    }
                }
            }

            val model = Model("root", listOf(Item("A", "Apache"), Item("B", "Bing")))
            routing {
                get("/v1") {
                    call.respond(model)
                }
                get("/v1/item/{key}") {
                    call.respond(model.items.first { it.key == call.parameters["key"] })
                }
            }
        }
    }
}

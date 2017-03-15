package org.jetbrains.ktor.samples.json

import com.google.gson.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.transform.*

class JsonResponse(val data: Any)
data class Model(val name: String, val items: List<Item>)
data class Item(val key: String, val value: String)

/*
         > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1
         {"name":"root","items":[{"key":"A","value":"Apache"},{"key":"B","value":"Bing"}]}

         > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1/item/A
         {"key":"A","value":"Apache"}
     */

fun Application.main() {
    install(DefaultHeaders)
    install(Compression)
    install(CallLogging)

    val gson = GsonBuilder().create()
    intercept(ApplicationCallPipeline.Infrastructure) { call ->
        if (call.request.acceptItems().any { it.value == "application/json" }) {
            call.transform.register<JsonResponse> { value ->
                TextContent(gson.toJson(value.data), ContentType.Application.Json)
            }
        }
    }

    val model = Model("root", listOf(Item("A", "Apache"), Item("B", "Bing")))

    // Ktor Sample:: Json (Jetty)
    // curl -v --compress --header "Accept: application/json" http://localhost:8080/v1
    routing {
        get("/v1") {
            contentType(ContentType.Application.Json) {
                println("JSON, as expected")
            }
            contentType(ContentType.Video.MP4) {
                println("Will never happen, we are safe")
                throw Exception("Oops, I didn't expected this branch to be executed!!")
            }
            call.respond(JsonResponse(model))
        }
        get("/v1/item/{key}") {
            call.respond(JsonResponse(model.items.first { it.key == call.parameters["key"] }))
        }
    }
}

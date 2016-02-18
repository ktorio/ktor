package org.jetbrains.ktor.samples.json

import com.google.gson.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import java.util.zip.*

data class Model(val name: String, val items: List<Item>)
data class Item(val key: String, val value: String)

class JsonApplication(config: ApplicationConfig) : Application(config) {
    /*
         > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1
         {"name":"root","items":[{"key":"A","value":"Apache"},{"key":"B","value":"Bing"}]}

         > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1/item/A
         {"key":"A","value":"Apache"}
     */
    init {
        intercept { call ->
            if (call.request.acceptEncoding()?.contains("deflate") ?: false) {
                call.response.headers.append(HttpHeaders.ContentEncoding, "deflate")
                call.response.interceptStream { content, stream ->
                    stream {
                        DeflaterOutputStream(this).use(content)
                    }
                }
            }
        }

        intercept { call ->
            if (call.request.accept() == "application/json") {
                call.interceptRespond { value, send ->
                    if (value is Model)
                        send(TextContent(ContentType.Application.Json, GsonBuilder().create().toJson(value)))
                    else
                        send(value)
                }
            }
        }

        val model = Model("root", listOf(Item("A", "Apache"), Item("B", "Bing")))
        routing {
            get("/v1") {
                respond(model)
            }
            get("/v1/item/{key}") {
                respond(model.items.first { it.key == parameters["key"] })
            }
        }
    }
}

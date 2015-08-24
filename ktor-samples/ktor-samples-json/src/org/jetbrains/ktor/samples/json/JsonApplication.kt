package org.jetbrains.ktor.samples.json

import com.google.gson.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import java.util.zip.*

data class Model(val name: String, val items: List<Item>)
data class Item(val key: String, val value: String)

class JsonApplication(config: ApplicationConfig) : Application(config) {
    /*
         > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1
         {"name":"root","items":[{"key":"A","value":"Apache"},{"key":"B","value":"Bing"}]}
     */
    init {
        intercept { next ->
            when {
                request.acceptEncoding()?.contains("deflate") ?: false -> {
                    response.header("Content-Encoding", "deflate")
                    response.interceptStream { content, stream ->
                        stream {
                            DeflaterOutputStream(this).apply {
                                content()
                                close()
                            }
                        }
                    }
                }
            }

            next()
        }

        intercept { next ->
            if (request.accept() == "application/json") {
                    response.interceptSend { value, send ->
                        if (value is Model)
                            response.sendText(ContentType.Application.Json, GsonBuilder().create().toJson(value))
                        else
                            send(value)
                    }
            }
            next()
        }

        routing {
            get("/v1") {
                response.send(Model("root", listOf(Item("A", "Apache"), Item("B", "Bing"))))
            }
        }
    }
}

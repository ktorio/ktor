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
        handler.intercept { context, next ->
            when {
                context.request.acceptEncoding()?.contains("deflate") ?: false -> {
                    context.response.header("Content-Encoding", "deflate")
                    context.response.stream.intercept { content, stream ->
                        stream {
                            DeflaterOutputStream(this).apply {
                                content()
                                close()
                            }
                        }
                    }
                }
            }

            next(context)
        }

        handler.intercept { context, handler ->
            if (context.request.accept() == "application/json") {
                with(context.response) {
                    send.intercept { value, send ->
                        sendText(ContentType.Application.Json, GsonBuilder().create().toJson(value))
                    }
                }
            }
            handler(context)
        }

        routing {
            get("/v1") {
                response.send(Model("root", listOf(Item("A", "Apache"), Item("B", "Bing"))))
            }
        }
    }
}

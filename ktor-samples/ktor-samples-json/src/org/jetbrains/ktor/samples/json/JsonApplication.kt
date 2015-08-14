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
         > curl --compress d--header "Accept: application/json" http://localhost:8080/v1
         {"name":"root","items":[{"key":"A","value":"Apache"},{"key":"B","value":"Bing"}]}
     */
    init {
        handler.intercept { request, handler ->
            when {
                request.acceptEncoding()?.contains("deflate") ?: false -> {
                    request.createResponse.intercept { createResponse ->
                        createResponse().apply {
                            header("Content-Encoding", "deflate")
                            stream.intercept { content, stream ->
                                stream {
                                    DeflaterOutputStream(this).apply {
                                        content()
                                        close()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            handler(request)
        }

        handler.intercept { request, handler ->
            if (request.accept() == "application/json") {
                request.createResponse.intercept { createResponse ->
                    createResponse().apply {
                        send.intercept { value, send ->
                            contentType(ContentType.Application.Json)
                            sendText(GsonBuilder().create().toJson(value))
                        }
                    }
                }
            }
            handler(request)
        }

        routing {
            get("/v1") {
                respond {
                    send(Model("root", listOf(Item("A", "Apache"), Item("B", "Bing"))))
                }
            }
        }
    }
}

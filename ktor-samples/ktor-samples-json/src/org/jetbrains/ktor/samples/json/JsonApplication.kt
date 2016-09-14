package org.jetbrains.ktor.samples.json

import com.google.gson.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*

data class Model(val name: String, val items: List<Item>)
data class Item(val key: String, val value: String)

class JsonApplication : ApplicationFeature<Application, Unit, Unit> {
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

            val gson = GsonBuilder().create()
            intercept(ApplicationCallPipeline.Infrastructure) { call ->
                if (HeaderValue("application/json") in call.request.acceptItems()) {
                    call.transform.register<Any> { value ->
                        val responseContentType = call.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                        when (responseContentType) {
                            ContentType.Application.Json -> TextContent(ContentType.Application.Json, gson.toJson(value))
                            else -> value
                        }
                    }
                }
            }

            val model = Model("root", listOf(Item("A", "Apache"), Item("B", "Bing")))
            routing {
                get("/v1") {
                    call.respond(ContentType.Application.Json, model)
                }
                get("/v1/item/{key}") {
                    call.respond(ContentType.Application.Json, model.items.first { it.key == call.parameters["key"] })
                }
            }
        }
    }
}

fun ApplicationCall.respond(contentType: ContentType, value: Any): Nothing {
    response.contentType(contentType)
    respond(value)
}

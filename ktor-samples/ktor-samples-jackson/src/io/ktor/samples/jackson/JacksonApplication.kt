package io.ktor.samples.jackson

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.datatype.jsr310.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.*
import java.time.*

data class Model(val name: String, val items: List<Item>, val yearMonth: Year = Year.now())
data class Item(val key: String, val value: String)

/*
         > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1
         {"name":"root","items":[{"key":"A","value":"Apache"},{"key":"B","value":"Bing"}]}
         The result is pretty printed, to show off how to configure the objectmapper, but it is
         possible to use the default objectMapper as well

         > curl -v --compress --header "Accept: application/json" http://localhost:8080/v1/item/A
         {"key":"A","value":"Apache"}
     */

fun Application.main() {

    install(DefaultHeaders)
    install(Compression)
    install(CallLogging)
    install(ContentNegotiation) {
      jackson {
        configure(SerializationFeature.INDENT_OUTPUT, true)
        registerModule(JavaTimeModule())
      }
    }

    val model = Model("root", listOf(Item("A", "Apache"), Item("B", "Bing")))
    routing {
        get("/v1") {
            call.respond(model)
        }
        get("/v1/item/{key}") {
            val item = model.items.firstOrNull { it.key == call.parameters["key"] }
            if (item == null)
                call.respond(HttpStatusCode.NotFound)
            else
                call.respond(item)
        }
    }
}

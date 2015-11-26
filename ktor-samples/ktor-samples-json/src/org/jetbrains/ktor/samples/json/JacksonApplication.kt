package org.jetbrains.ktor.samples.json

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.jackson.*
import org.jetbrains.ktor.locations.*
import org.jetbrains.ktor.routing.*

class JacksonApplication(config: ApplicationConfig) : Application(config) {
    var currentModel = Model("empty", emptyList())

    init {
        setupJackson()

        locations {
            get("/model") {
                response.send(JsonContent(currentModel))
            }
            get("/model/item/{key}") {
                response.send(JsonContent(currentModel.items.first { it.key == parameters["key"] }))
            }
            put("/model") {
                withJsonContent<Model> { model ->
                    if (model != null) {
                        currentModel = model
                    }

                    response.send(JsonContent(currentModel))
                }
            }
        }
    }
}
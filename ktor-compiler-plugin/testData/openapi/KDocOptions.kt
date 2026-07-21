package openapi

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.toMap

fun Application.installKDocOptions() {
    routing {
        /**
         * This endpoint is for testing type references.
         *
         * @tag references
         * @response 429 application/json [kotlin.time.Instant]? Is it tea time already?
         * @response 200 application/json :[String]+
         */
        get("/type-references") {
            call.respond(call.parameters.toMap())
        }

        /**
         * Let's use some attributes.
         *
         * @tag attributes
         * @query query [String] my input
         *   pattern: [a-z0-9_&+]+
         *   nullable: true
         * @query count [Int]
         *   minimum: 1
         *   maximum: 100
         * @body [io.ktor.openapi.OpenApiInfo]+
         *   maxItems: 10
         */
        get("/attributes") {
            val openApiInfo = call.receive<OpenApiInfo>()
            val query = call.parameters["query"]
            val count = call.parameters["count"]?.toIntOrNull()
            call.respond(openApiInfo)
        }

        /**
         * Let's try some parameters.
         *
         * @header X-Rate-Limit-Limit [Int] The number of allowed requests in the current period
         * @cookie token A token
         *   x-sensitive: true
         * @path   id      The user ID
         * @query  q       A search query
         * @header X-Type  The type of a thing
         *   enum: [String, Int]
         */
        get("/parameters/{id}") {
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
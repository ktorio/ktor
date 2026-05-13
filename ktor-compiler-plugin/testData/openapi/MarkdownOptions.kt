package openapi

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.toMap

fun Application.installMarkdownOptions() {
    routing {
        /**
         * This endpoint is for testing type references.
         *
         * - Tag: references
         * - Responses:
         *   - 429 application/json [kotlin.time.Instant]? Is it tea time already?
         *   - 200 application/json :[String]+
         */
        get("/type-references") {
            call.respond(call.parameters.toMap())
        }

        /**
         * Let's use some attributes.
         *
         * Tag: attributes
         * Query parameters:
         *  - query [String] my input
         *      pattern: [a-z0-9_&+]+
         *      nullable: true
         *  - count [Int]
         *      minimum: 1
         *      maximum: 100
         * Body: [io.ktor.openapi.OpenApiInfo]+
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
         * - OperationId: getSomeParameters
         * - Header: X-Rate-Limit-Limit [Int] The number of allowed requests in the current period
         * - Cookie: token A token
         *   x-sensitive: true
         * - Path:  id      The user ID
         * - Query: q       A search query
         * - Header: X-Type  The type of a thing
         *   enum: [String, Int]
         */
        get("/parameters/{id}") {
            call.respond(HttpStatusCode.NoContent)
        }

        /**
         * This endpoint should not be processed.
         *
         * Ignore
         */
        get("/unprocessed") {
            call.respondText("Hello, world!")
        }

        /**
         * External type reference.
         *
         * Response: 200 [io.ktor.openapi.Server] A server
         */
        get("/server") {
            call.respond(HttpStatusCode.BadGateway)
        }
    }
}

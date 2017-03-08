package org.jetbrains.ktor.samples.httpbin

import jsonOf
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.TextContent
import org.jetbrains.ktor.features.Compression
import org.jetbrains.ktor.features.DefaultHeaders
import org.jetbrains.ktor.http.ContentType
import org.jetbrains.ktor.http.HttpStatusCode
import org.jetbrains.ktor.logging.CallLogging
import org.jetbrains.ktor.pipeline.PipelineContext
import org.jetbrains.ktor.request.header
import org.jetbrains.ktor.request.location
import org.jetbrains.ktor.response.header
import org.jetbrains.ktor.response.respondText
import org.jetbrains.ktor.routing.get
import org.jetbrains.ktor.routing.routing
import org.jetbrains.ktor.transform.transform
import org.jetbrains.ktor.util.ValuesMap

class Something


class JsonResponse(
    var args: ValuesMap? = null,
    var headers: ValuesMap? = null,
    var origin: String? = null,
    var url: String? = null,
    var `user-agent`: String? = null
)


fun Application.main() {
    install(DefaultHeaders)
    install(Compression)
    install(CallLogging)

    intercept(ApplicationCallPipeline.Infrastructure) { call ->
        with(call.response) {
            header("Access-Control-Allow-Credentials", "true")
            header("Access-Control-Allow-Origin", "*")
        }
        call.transform.register { value: JsonResponse ->
            TextContent(jsonOf(value), ContentType.Application.Json)
        }
    }

    routing {
        get("/") {
            call.respondText("it works great")
        }
        get("/get") {
            val that: PipelineContext<ApplicationCall> = this
            val response = JsonResponse(
                args = call.request.queryParameters,
                headers = call.request.headers,
                origin = call.request.local.remoteHost,
                url = call.request.location()
            )
            call.respond(response)
        }
        get("/cache") {
            val etag = "db7a0a2684bb439e858ee25ae5b9a5c6"
            val requested = call.request.header("If-None-Match")
            if (requested == etag) {
                with(call.response) {
                    status(HttpStatusCode.NotModified)
                    TextContent("")
                }
            } else {
                val response = JsonResponse(
                    args = call.request.queryParameters,
                    headers = call.request.headers,
                    origin = call.request.local.remoteHost,
                    url = call.request.location()
                )
                with(call.response) {
                    header("ETag", etag)
                }
                call.respond(response)
            }

        }
        get("/user-agent") {
            val response = JsonResponse(
                `user-agent` = call.request.header("User-Agent")
            )
            call.respond(response)
        }
        get("/status/{status}") {
            val status = call.parameters.get("status")?.toInt() ?: 0
            call.response.status(HttpStatusCode.fromValue(status) ?: HttpStatusCode.BadRequest)
            call.respond("")
        }
    }
}

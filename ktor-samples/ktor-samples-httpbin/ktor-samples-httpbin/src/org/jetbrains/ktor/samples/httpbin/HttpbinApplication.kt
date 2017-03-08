package org.jetbrains.ktor.samples.httpbin

import jsonOf
import moshi
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
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.transform.transform
import org.jetbrains.ktor.util.ValuesMap
import parseJson

class Something


class JsonResponse(
    var args: ValuesMap? = null,
    var headers: ValuesMap? = null,
    var origin: String? = null,
    var url: String? = null,
    var `user-agent`: String? = null,
    var data: String? = null,
    var files: String? = null,
    var form: ValuesMap? = null,
    var json: Map<String, Any>? = null
)

suspend fun ApplicationCall.sendResponse(operation: JsonResponse.() -> Unit) {
    val response = JsonResponse(
        args = request.queryParameters,
        headers = request.headers
    )
    response.operation()
    respond(response)
}


fun Route.contentTypeRequest(contentType: ContentType, build: Route.() -> Unit): Route {
    return header("Content-Type", "${contentType.contentType}/${contentType.contentSubtype}", build)
}


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
        route("/post") {

            contentTypeRequest(ContentType.MultiPart.FormData) {
                post {
                    call.sendResponse {
                        form = call.request.content.get<ValuesMap>()
                    }
                }
            }
            contentTypeRequest(ContentType.Application.FormUrlEncoded) {
                post {
                    call.sendResponse {
                        form = call.request.content.get<ValuesMap>()
                    }
                }
            }
            contentTypeRequest(ContentType.Application.Json) {
                post {
                    call.sendResponse {
                        val content = call.request.content.get<String>()
                        json = parseJson(content)
                    }
                }
            }
            post {
                call.sendResponse {
                    data = call.request.content.get<String>()
                }
            }
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

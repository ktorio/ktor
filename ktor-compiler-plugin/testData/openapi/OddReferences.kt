// RUN_PIPELINE_TILL: BACKEND

package openapi

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

private val myFileScopedParameter = "fileScoped"
public const val myPublicConstant = "publicConstant"

fun Application.installOddReferences() {
    val body = "My response"
    val accepted = HttpStatusCode.Accepted

    routing {
        /**
         * Referencing from a higher scope
         */
        get("/higher-scope") {
            call.respondText(
                "$body ${call.parameters[myFileScopedParameter]} ${call.parameters[myPublicConstant]}",
                contentType = ContentType.Text.CSV,
                status = accepted
            )
        }

        /**
         * Local declarations; external function return
         */
        get("/external-return") {
            val responseMessage = "My response"
            val customResponse = CustomResponse(responseMessage)
            val responseStatus = acceptedStatus()

            call.respond(
                status = responseStatus,
                message = customResponse,
            )
        }

        /**
         * Reassignment; branches
         */
        get("/reassignment") {
            var contentType = ContentType("application", "message+json")
            if (call.request.queryParameters["plain"] == "true")
                contentType = ContentType.Text.Plain

            call.respondText(body, contentType = contentType)
        }

        /**
         * Calls with lambda argument
         */
        get("/function-lambda") {
            callLambdaArg {
                call.respondText(
                    body,
                    contentType = ContentType.Text.CSV,
                    status = accepted
                )
            }
        }

        /**
         * Local variables that can't be extracted
         */
        get("/set-cookies") {
            for (n in call.queryParameters.names()) {
                call.response.cookies.append(
                    name = n,
                    value = call.queryParameters[n] ?: "",
                    path = "/",
                )
            }
        }
    }
}

fun acceptedStatus() =
    HttpStatusCode.Accepted

fun callLambdaArg(action: suspend RoutingContext.() -> Unit) {}


@Serializable
data class CustomResponse(val message: String)

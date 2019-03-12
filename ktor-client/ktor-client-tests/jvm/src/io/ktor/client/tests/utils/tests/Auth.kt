package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*

fun Application.authTestServer() {
    install(Authentication) {
        basic("test-basic") {
            realm = "my-server"
            validate { call ->
                if (call.name == "user1" && call.password == "Password1")
                    UserIdPrincipal("user1")
                else null
            }
        }
    }

    routing {
        route("/auth") {
            route("/basic") {
                authenticate("test-basic") {
                    post {
                        val requestData = call.receiveText()
                        if (requestData == "{\"test\":\"text\"}")
                            call.respondText("OK")
                        else
                            call.respond(HttpStatusCode.BadRequest)
                    }
                    route("/ws") {
                        route("/echo") {
                            webSocket(protocol = "ocpp2.0,ocpp1.6") {
                                for (message in incoming) {
                                    send(message)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

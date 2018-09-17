package io.ktor.tests.jrpc.routers

import io.ktor.jrpc.routers.JrpcRouter

val secretRequest = "Hello there!"
val secretResponse = "General Kenobi..."
val emptyResponse = "Silence?"
val methodEcho = "echo"

val echoRouter = object : JrpcRouter() {

    init {
        method<List<String>>(methodEcho) { echo(it) }
    }

    private fun echo(messages: List<String>): String {
        if (messages.isEmpty()) return emptyResponse
        return if (messages.size == 1 && messages[0] == secretRequest)
            secretResponse
        else
            messages.joinToString(System.lineSeparator())
    }
}


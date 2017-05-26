package org.jetbrains.ktor.samples.chat

import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.util.*
import org.jetbrains.ktor.websocket.*
import java.time.*

private val server = ChatServer()

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(Routing) {
        withSessions<Session> {
            withCookieByValue()
        }

        intercept(ApplicationCallPipeline.Infrastructure) {
            if (call.sessionOrNull<Session>() == null) {
                call.session(Session(nextNonce()))
            }
        }

        webSocket("/ws") {
            val session = call.sessionOrNull<Session>()
            if (session == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
                return@webSocket
            }

            pingInterval = Duration.ofMinutes(1)

            server.memberJoin(session.id, this)

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        receivedMessage(session.id, frame.readText())
                    }
                }
            } finally {
                server.memberLeft(session.id, this)
            }
        }

        static {
            defaultResource("index.html", "web")
            resources("web")
        }
    }
}

data class Session(val id: String)

private suspend fun receivedMessage(id: String, command: String) {
    when {
        command.startsWith("/who") -> server.who(id)
        command.startsWith("/user") -> {
            val newName = command.removePrefix("/user").trim()
            when {
                newName.isEmpty() -> server.sendTo(id, "server::help", "/user [newName]")
                newName.length > 50 -> server.sendTo(id, "server::help", "new name is too long: 50 characters limit")
                else -> server.memberRenamed(id, newName)
            }
        }
        command.startsWith("/help") -> server.help(id)
        command.startsWith("/") -> server.sendTo(id, "server::help", "Unknown command ${command.takeWhile { !it.isWhitespace() }}")
        else -> server.message(id, command)
    }
}

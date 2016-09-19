package org.jetbrains.ktor.samples.chat

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.sessions.*
import org.jetbrains.ktor.util.*
import org.jetbrains.ktor.websocket.*
import java.time.*

class ChatApplication() : ApplicationModule() {
    val server = ChatServer()

    override fun Application.install() {
        install(DefaultHeaders)
        install(CallLogging)

        routing {
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

                handle { frame ->
                    if (frame is Frame.Text) {
                        receivedMessage(session.id, frame.readText())
                    }
                }

                close { reason ->
                    server.memberLeft(session.id, this)
                }
            }

            serveClasspathResources("web")
        }
    }

    data class Session(val id: String)

    private fun receivedMessage(id: String, command: String) {
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

    private inline fun String.ifEmpty(block: () -> String) = if (isEmpty()) block() else this
}

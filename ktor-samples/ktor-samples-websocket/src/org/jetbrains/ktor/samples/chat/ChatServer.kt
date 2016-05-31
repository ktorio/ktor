package org.jetbrains.ktor.samples.chat

import org.jetbrains.ktor.util.*
import org.jetbrains.ktor.websocket.*
import java.nio.*
import java.util.*
import java.util.concurrent.*

class ChatServer {
    private val memberName = AttributeKey<String>("char.member-name")
    val members = ConcurrentHashMap<String, WebSocket>()
    val lastMessages = LinkedList<String>()

    fun memberJoin(member: String, socket: WebSocket, initialName: String) {
        socket.call.attributes.put(memberName, initialName)
        members[member] = socket
        broadcast("server", "Member joined: $initialName.")
        val messages = synchronized(lastMessages) { lastMessages.toList() }

        for (message in messages) {
            socket.send(Frame.Text(message))
        }
    }

    fun memberRenamed(member: String, to: String) {
        val socket = members[member] !!
        val oldName = socket.memberName() ?: member
        socket.call.attributes.put(memberName, to)
        broadcast("server", "Member renamed from $oldName to $to")
    }

    fun memberLeaved(member: String) {
        val name = members.remove(member)?.memberName() ?: member
        broadcast("server", "Member leaved: $name.")
    }

    fun who(sender: String) {
        members[sender]?.send(Frame.Text(members.entries.joinToString(prefix = "[server::who] ") { it.value.memberName() ?: it.key }))
    }

    fun help(sender: String) {
        members[sender]?.send(Frame.Text("[server::help] Possible commands are: /user, /help and /who"))
    }

    fun sendTo(receipient: String, sender: String, message: String) {
        members[receipient]?.send(Frame.Text("[$sender] $message"))
    }

    fun message(sender: String, message: String) {
        val name = members[sender]?.memberName() ?: sender
        val formatted = "[$name] $message"

        broadcast(formatted)
        synchronized(lastMessages) {
            lastMessages.add(formatted)
            if (lastMessages.size > 100) {
                lastMessages.removeFirst()
            }
        }
    }

    fun broadcast(message: String) {
        broadcast(buildByteBuffer {
            putString(message, Charsets.UTF_8)
        })
    }

    fun broadcast(sender: String, message: String) {
        val name = members[sender]?.memberName() ?: sender
        broadcast("[$name] $message")
    }

    fun broadcast(serialized: ByteBuffer) {
        members.values.forEach { socket ->
            socket.send(Frame.Text(true, serialized.duplicate()))
        }
    }

    private fun WebSocket.memberName() = call.attributes.getOrNull(memberName)
}
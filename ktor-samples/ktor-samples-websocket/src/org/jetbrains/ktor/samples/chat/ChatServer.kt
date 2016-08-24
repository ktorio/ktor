package org.jetbrains.ktor.samples.chat

import org.jetbrains.ktor.util.*
import org.jetbrains.ktor.websocket.*
import java.nio.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class ChatServer {
    val usersCounter = AtomicInteger()
    val memberNames = ConcurrentHashMap<String, String>()
    val members = ConcurrentHashMap<String, MutableList<WebSocket>>()
    val lastMessages = LinkedList<String>()

    fun memberJoin(member: String, socket: WebSocket) {
        val name = memberNames.computeIfAbsent(member) { "user${usersCounter.incrementAndGet()}" }
        val list = members.computeIfAbsent(member) { CopyOnWriteArrayList<WebSocket>() }
        list.add(socket)

        if (list.size == 1) {
            broadcast("server", "Member joined: $name.")
        }

        val messages = synchronized(lastMessages) { lastMessages.toList() }
        for (message in messages) {
            socket.send(Frame.Text(message))
        }
    }

    fun memberRenamed(member: String, to: String) {
        val oldName = memberNames.put(member, to) ?: member
        broadcast("server", "Member renamed from $oldName to $to")
    }

    fun memberLeft(member: String, socket: WebSocket) {
        val connections = members[member]
        connections?.remove(socket)

        if (connections != null && connections.isEmpty()) {
            val name = memberNames[member] ?: member
            broadcast("server", "Member left: $name.")
        }
    }

    fun who(sender: String) {
        members[sender]?.send(Frame.Text(memberNames.keys.joinToString(prefix = "[server::who] ")))
    }

    fun help(sender: String) {
        members[sender]?.send(Frame.Text("[server::help] Possible commands are: /user, /help and /who"))
    }

    fun sendTo(receipient: String, sender: String, message: String) {
        members[receipient]?.send(Frame.Text("[$sender] $message"))
    }

    fun message(sender: String, message: String) {
        val name = memberNames[sender] ?: sender
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
        val name = memberNames[sender] ?: sender
        broadcast("[$name] $message")
    }

    fun broadcast(serialized: ByteBuffer) {
        members.values.forEach { socket ->
            socket.send(Frame.Text(true, serialized.duplicate()))
        }
    }

    fun List<WebSocket>.send(frame: Frame) {
        forEach { it.send(frame.copy()) }
    }
}

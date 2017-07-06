package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*

class SessionTrackerById(val type: KClass<*>, val serializer: SessionSerializer, val storage: SessionStorage, val sessionIdProvider: () -> String) : SessionTracker {
    private val SessionIdKey = AttributeKey<String>("SessionId")

    suspend override fun load(call: ApplicationCall, transport: String?): Any? {
        val sessionId = transport ?: return null

        call.attributes.put(SessionIdKey, sessionId)
        try {
            val session = storage.read(sessionId) { channel ->
                // TODO: read text without blocking
                val text = channel.toInputStream().bufferedReader().readText()
                serializer.deserialize(text)
            }
            return session
        } catch (notFound: NoSuchElementException) {
            call.application.log.debug("Failed to lookup session: $notFound")
        }
        return null
    }

    override suspend fun store(call: ApplicationCall, value: Any): String {
        val sessionId = call.attributes.computeIfAbsent(SessionIdKey, sessionIdProvider)
        val serialized = serializer.serialize(value)
        storage.write(sessionId) { channel ->
            // TODO: write text without blocking
            channel.toOutputStream().bufferedWriter().use { writer ->
                writer.write(serialized)
            }
        }
        return sessionId
    }

    override suspend fun clear(call: ApplicationCall) {
        val sessionId = call.attributes.getOrNull(SessionIdKey)
        if (sessionId != null) {
            call.attributes.remove(SessionIdKey)
            storage.invalidate(sessionId)
        }
    }

    override fun validate(value: Any) {
        if (!type.javaObjectType.isAssignableFrom(value.javaClass)) {
            throw IllegalArgumentException("Value for this session tracker expected to be of type $type but was $value")
        }
    }
}

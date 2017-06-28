package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*


fun <S : Any> Sessions.Configuration.cookieById(sessionType: KClass<S>, storage: SessionStorage, block: CookieByIdSessionTrackerBuilder<S>.() -> Unit = {}) {
    CookieByIdSessionTrackerBuilder(sessionType, storage).apply {
        block()
        tracker = build()
    }
}

class CookieByIdSessionTrackerBuilder<S : Any>(val type: KClass<S>, val storage: SessionStorage) {
    var sessionIdProvider = { nextNonce() }
    var cookieName: String = "SESSION_ID"
    var serializer: SessionSerializer = autoSerializerOf(type)

    fun build(): SessionTracker = SessionTrackerCookieById(type, sessionIdProvider, cookieName, serializer, storage)
}

internal class SessionTrackerCookieById(val type: KClass<*>, val sessionIdProvider: () -> String = { nextNonce() }, val cookieName: String = "SESSION_ID", val serializer: SessionSerializer, val storage: SessionStorage) : SessionTracker {

    private val SessionIdKey = AttributeKey<String>("SessionId")

    override suspend fun assign(call: ApplicationCall, session: Any, cookieSettings: SessionCookiesSettings) {
        val sessionId = call.attributes.computeIfAbsent(SessionIdKey, sessionIdProvider)
        val serialized = serializer.serialize(session)
        storage.save(sessionId) { channel ->
            channel.toOutputStream().bufferedWriter().use { writer ->
                writer.write(serialized)
            }
        }
        call.response.cookies.append(cookieSettings.toCookie(cookieName, sessionId))
    }

    override suspend fun lookup(context: PipelineContext<Unit>, cookieSettings: SessionCookiesSettings): Any? {
        val call = context.call
        val sessionId = cookieSettings.fromCookie(call.request.cookies[cookieName]) ?: return null

        call.attributes.put(SessionIdKey, sessionId)
        try {
            val session = storage.read(sessionId) { channel ->
                val text = channel.toInputStream().bufferedReader().readText()
                serializer.deserialize(text)
            }
            return session
        } catch (notFound: NoSuchElementException) {
            call.application.log.debug("Failed to lookup session: $notFound")
        }
        return null
    }

    override suspend fun unassign(call: ApplicationCall) {
        call.attributes.remove(SessionIdKey)

        call.request.cookies[cookieName]?.let { sessionId ->
            call.response.cookies.appendExpired(cookieName)
            storage.invalidate(sessionId)
        }
    }

    override fun validate(value: Any) {
        if (!type.javaObjectType.isAssignableFrom(value.javaClass)) {
            throw IllegalArgumentException("Value for this session tracker expected to be of type $type but was $value")
        }
    }
}

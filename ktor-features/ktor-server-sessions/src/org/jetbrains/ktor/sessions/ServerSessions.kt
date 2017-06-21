package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*
import kotlin.reflect.*


fun <S : Any> SessionConfigBuilder<S>.withCookieBySessionId(storage: SessionStorage, block: CookieByIdSessionTrackerBuilder<S>.() -> Unit = {}) {
    CookieByIdSessionTrackerBuilder(sessionType, storage).apply {
        block()
        sessionTracker = build()
    }
}

class CookieByIdSessionTrackerBuilder<S : Any>(val type: KClass<S>, val storage: SessionStorage) {
    private var execBuilder = { Executors.newCachedThreadPool() }

    var sessionIdProvider = { nextNonce() }
    var settings = SessionCookiesSettings()
    var cookieName: String = "SESSION_ID"
    var serializer: SessionSerializer<S> = autoSerializerOf(type)

    fun withExecutorService(exec: ExecutorService) {
        execBuilder = { exec }
    }

    fun build(): SessionTracker<S> = CookieByIdSessionTracker(execBuilder(), sessionIdProvider, settings, cookieName, serializer, storage)
}

internal class CookieByIdSessionTracker<S : Any>(val exec: ExecutorService, val sessionIdProvider: () -> String = { nextNonce() }, val settings: SessionCookiesSettings, val cookieName: String = "SESSION_ID", val serializer: SessionSerializer<S>, val storage: SessionStorage) : SessionTracker<S> {

    private val SessionIdKey = AttributeKey<String>("SessionId")

    override suspend fun assign(call: ApplicationCall, session: S) {
        val sessionId = call.attributes.computeIfAbsent(SessionIdKey, sessionIdProvider)
        val serialized = serializer.serialize(session)
        storage.save(sessionId) { channel ->
            channel.toOutputStream().bufferedWriter().use { writer ->
                writer.write(serialized)
            }
        }
        call.response.cookies.append(settings.toCookie(cookieName, sessionId))
    }

    override suspend fun lookup(context: PipelineContext<Unit>, processSession: (S) -> Unit) {
        val call = context.call
        val cookie = call.request.cookies[cookieName]
        val sessionId = if (cookie != null) cookie else {
            context.proceed()
            return
        }

        call.attributes.put(SessionIdKey, sessionId)
        try {
            storage.read(sessionId) { channel ->
                val text = channel.toInputStream().bufferedReader().readText()
                val session = serializer.deserialize(text)
                processSession(session)
            }
        } catch (notFound: NoSuchElementException) {
            call.application.log.debug("Failed to lookup session: $notFound")
        }
    }

    override suspend fun unassign(call: ApplicationCall) {
        call.attributes.remove(SessionIdKey)

        call.request.cookies[cookieName]?.let { sessionId ->
            call.response.cookies.appendExpired(cookieName)
            storage.invalidate(sessionId)
        }
    }
}

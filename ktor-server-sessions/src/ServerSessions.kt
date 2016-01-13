package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*
import kotlin.reflect.*


fun <S : Any> HasTracker<S>.withCookieBySessionId(storage: SessionStorage, block: CookieByIdSessionTrackerBuilder<S>.() -> Unit = {}) {
    CookieByIdSessionTrackerBuilder(type, storage).apply {
        block()
        sessionTracker = build()
    }
}

class CookieByIdSessionTrackerBuilder<S : Any>(val type: KClass<S>, val storage: SessionStorage) : HasSerializer<S> {
    private var execBuilder = { Executors.newCachedThreadPool() }

    var sessionIdProvider = { nextNonce() }
    var settings = CookiesSettings()
    var cookieName: String = "SESSION_ID"
    override var serializer: SessionSerializer<S> = autoSerializerOf(type)

    fun withExecutorService(exec: ExecutorService) {
        execBuilder = { exec }
    }

    fun build(): SessionTracker<S> = CookieByIdSessionTracker(execBuilder(), sessionIdProvider, settings, cookieName, serializer, storage)
}

internal class CookieByIdSessionTracker<S : Any>(val exec: ExecutorService, val sessionIdProvider: () -> String = { nextNonce() }, val settings: CookiesSettings, val cookieName: String = "SESSION_ID", val serializer: SessionSerializer<S>, val storage: SessionStorage) : SessionTracker<S> {

    private val SessionIdKey = AttributeKey<String>()

    override fun assign(context: ApplicationRequestContext, session: S) {
        val sessionId = context.attributes.computeIfAbsent(SessionIdKey, sessionIdProvider)
        val serialized = serializer.serialize(session)
        storage.save(sessionId) { out ->
            out.bufferedWriter().use { writer ->
                writer.write(serialized)
            }
        }
        context.response.cookies.append(settings.newCookie(cookieName, sessionId))
    }

    override fun lookup(context: ApplicationRequestContext, injectSession: (S) -> Unit, next: ApplicationRequestContext.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        val sessionId = context.request.cookies[cookieName]
        return if (sessionId == null) {
            next(context)
        } else {
            context.attributes.put(SessionIdKey, sessionId)
            context.handleAsync(exec, {
                storage.read(sessionId) { input ->
                    val text = input.bufferedReader().readText() // TODO what can we do if failed?
                    context.handleAsync(exec, {
                        val session = serializer.deserialize(text)
                        injectSession(session)
                        next(context)
                    }, failBlock = {})
                }
                ApplicationRequestStatus.Asynchronous
            }, failBlock = {})

            ApplicationRequestStatus.Asynchronous
        }
    }

    override fun unassign(context: ApplicationRequestContext) {
        context.attributes.remove(SessionIdKey)

        context.request.cookies[cookieName]?.let { sessionId ->
            context.response.cookies.appendExpired(cookieName)
            storage.invalidate(sessionId)
        }
    }
}

package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.time.*
import java.time.temporal.*
import java.util.concurrent.*
import kotlin.reflect.*

interface SessionTracker<S : Any> {
    fun lookup(context: ApplicationRequestContext, injectSession: (S) -> Unit, next: ApplicationRequestContext.() -> ApplicationRequestStatus): ApplicationRequestStatus
    fun assign(context: ApplicationRequestContext, session: S)
    fun unassign(context: ApplicationRequestContext)
}

data class CookiesSettings(
        val expireIn: TemporalAmount = Duration.ofDays(30),
        val requireHttps: Boolean = false
)

private fun CookiesSettings.newCookie(name: String, value: String) = Cookie(name, value, httpOnly = true, secure = requireHttps, expires = LocalDateTime.now().plus(expireIn))

private class CookieByValueSessionTracker<S : Any>(val settings: CookiesSettings, val cookieName: String, val serializer: SessionSerializer<S>) : SessionTracker<S> {
    override fun assign(context: ApplicationRequestContext, session: S) {
        context.response.cookies.append(settings.newCookie(cookieName, serializer.serialize(session)))
    }

    override fun lookup(context: ApplicationRequestContext, injectSession: (S) -> Unit, next: ApplicationRequestContext.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        val cookie = context.request.cookies[cookieName]
        if (cookie != null) {
            injectSession(serializer.deserialize(cookie))
        }
        return next(context)
    }

    override fun unassign(context: ApplicationRequestContext) {
        context.response.cookies.appendExpired(cookieName)
    }
}

private class CookieByIdSessionTracker<S : Any>(val exec: ExecutorService, val sessionIdProvider: () -> String = { nextNonce() }, val settings: CookiesSettings, val cookieName: String = "SESSION_ID", val serializer: SessionSerializer<S>, val storage: SessionStorage) : SessionTracker<S> {

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
            exec.submit {
                storage.read(sessionId) { input ->
                    val text = input.bufferedReader().readText() // TODO what can we do if failed?
                    exec.submit {
                        val session = serializer.deserialize(text)
                        injectSession(session)
                        next(context)
                        context.close()
                    }
                }
            }

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

interface HasType<S : Any> {
    val type: KClass<S>
}

interface HasTracker<S : Any> : HasType<S> {
    var sessionTracker: SessionTracker<S>
}

interface HasSerializer<S : Any> {
    var serializer: SessionSerializer<S>
}

fun <S : Any> HasTracker<S>.withCookieBySessionId(storage: SessionStorage, block: CookieByIdSessionTrackerBuilder<S>.() -> Unit) {
    CookieByIdSessionTrackerBuilder(type, storage).apply {
        block()
        sessionTracker = build()
    }
}

fun <S : Any> HasTracker<S>.withCookieByValue(block: CookieByValueSessionTrackerBuilder<S>.() -> Unit) {
    CookieByValueSessionTrackerBuilder(type).apply {
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

class CookieByValueSessionTrackerBuilder<S : Any>(val type: KClass<S>) : HasSerializer<S> {
    var settings = CookiesSettings()
    var cookieName: String = "SESSION"
    override var serializer: SessionSerializer<S> = autoSerializerOf(type)

    fun build(): SessionTracker<S> = CookieByValueSessionTracker(settings, cookieName, serializer)
}

interface SessionSerializer<T : Any> {
    fun serialize(session: T): String
    fun deserialize(s: String): T
}

package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.auth.crypto.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.time.*
import java.time.temporal.*
import java.util.concurrent.*
import kotlin.reflect.*

class SessionConfig<S : Any>(
        val type: KClass<S>,
        val sessionTracker: SessionTracker<S>
)

/**
 * SessionTracker provides ability to track and extract session by the context.
 * For example it could track it by cookie with session id or by IP address (could be good enough for corporate applications)
 */
interface SessionTracker<S : Any> {
    /**
     * Lookup session using the context, call [injectSession] if available and pass execution to the [next] in any case.
     * It is recommended to be async if there is external session store
     */
    fun lookup(context: ApplicationRequestContext, injectSession: (S) -> Unit, next: ApplicationRequestContext.() -> ApplicationRequestStatus): ApplicationRequestStatus

    /**
     * Assign session using the context. Override if there is existing session. Could be blocking.
     */
    fun assign(context: ApplicationRequestContext, session: S)

    /**
     * Unassign session if present. Does nothing if no session assigned.
     */
    fun unassign(context: ApplicationRequestContext)
}

/**
 * Represents a session cookie transformation. Useful for such things like signing and encryption
 */
interface CookieTransformer {
    fun transformRead(sessionCookieValue: String): String?
    fun transformWrite(sessionCookieValue: String): String
}

data class CookiesSettings(
        val expireIn: TemporalAmount = Duration.ofDays(30),
        val requireHttps: Boolean = false,
        val transformers: List<CookieTransformer> = emptyList()
)

private fun CookiesSettings.newCookie(name: String, value: String) =
        Cookie(name, value = transformers.fold(value) { value, t -> t.transformWrite(value) },
                httpOnly = true, secure = requireHttps, expires = LocalDateTime.now().plus(expireIn))

internal class CookieByValueSessionTracker<S : Any>(val settings: CookiesSettings, val cookieName: String, val serializer: SessionSerializer<S>) : SessionTracker<S> {
    override fun assign(context: ApplicationRequestContext, session: S) {
        context.response.cookies.append(settings.newCookie(cookieName, serializer.serialize(session)))
    }

    override fun lookup(context: ApplicationRequestContext, injectSession: (S) -> Unit, next: ApplicationRequestContext.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        val cookie = context.request.cookies[cookieName]
        var value = cookie
        for (t in settings.transformers) {
            if (value == null) {
                break
            }
            value = t.transformRead(value)
        }

        if (value != null) {
            injectSession(serializer.deserialize(value))
        }
        return next(context)
    }

    override fun unassign(context: ApplicationRequestContext) {
        context.response.cookies.appendExpired(cookieName)
    }
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

interface HasType<S : Any> {
    val type: KClass<S>
}

interface HasTracker<S : Any> : HasType<S> {
    var sessionTracker: SessionTracker<S>
}

interface HasSerializer<S : Any> {
    var serializer: SessionSerializer<S>
}

fun <S : Any> HasTracker<S>.withCookieBySessionId(storage: SessionStorage, block: CookieByIdSessionTrackerBuilder<S>.() -> Unit = {}) {
    CookieByIdSessionTrackerBuilder(type, storage).apply {
        block()
        sessionTracker = build()
    }
}

fun <S : Any> HasTracker<S>.withCookieByValue(block: CookieByValueSessionTrackerBuilder<S>.() -> Unit = {}) {
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

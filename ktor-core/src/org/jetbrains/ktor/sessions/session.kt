package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.time.*
import java.time.temporal.*
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
    fun lookup(call: ApplicationCall, injectSession: (S) -> Unit, next: ApplicationCall.() -> ApplicationCallResult): ApplicationCallResult

    /**
     * Assign session using the context. Override if there is existing session. Could be blocking.
     */
    fun assign(call: ApplicationCall, session: S)

    /**
     * Unassign session if present. Does nothing if no session assigned.
     */
    fun unassign(call: ApplicationCall)
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

fun CookiesSettings.newCookie(name: String, value: String) =
        Cookie(name, value = transformers.fold(value) { value, t -> t.transformWrite(value) },
                httpOnly = true, secure = requireHttps, expires = LocalDateTime.now().plus(expireIn))

internal class CookieByValueSessionTracker<S : Any>(val settings: CookiesSettings, val cookieName: String, val serializer: SessionSerializer<S>) : SessionTracker<S> {
    override fun assign(call: ApplicationCall, session: S) {
        call.response.cookies.append(settings.newCookie(cookieName, serializer.serialize(session)))
    }

    override fun lookup(call: ApplicationCall, injectSession: (S) -> Unit, next: ApplicationCall.() -> ApplicationCallResult): ApplicationCallResult {
        val cookie = call.request.cookies[cookieName]
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
        return next(call)
    }

    override fun unassign(call: ApplicationCall) {
        call.response.cookies.appendExpired(cookieName)
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

fun <S : Any> HasTracker<S>.withCookieByValue(block: CookieByValueSessionTrackerBuilder<S>.() -> Unit = {}) {
    CookieByValueSessionTrackerBuilder(type).apply {
        block()
        sessionTracker = build()
    }
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

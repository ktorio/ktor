package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import kotlin.reflect.*

class CookieValueSessionTracker<S : Any>(val cookieSettings: SessionCookiesSettings,
                                         val cookieName: String,
                                         val serializer: SessionSerializer<S>) : SessionTracker<S> {
    override suspend fun assign(call: ApplicationCall, session: S) {
        val serialized = serializer.serialize(session)
        val cookie = cookieSettings.toCookie(cookieName, serialized)
        call.response.cookies.append(cookie)
    }

    suspend override fun lookup(context: PipelineContext<Unit>, processSession: (S) -> Unit) {
        val cookie = context.call.request.cookies[cookieName]
        val value = cookieSettings.fromCookie(cookie)
        if (value != null) {
            val deserialize = serializer.deserialize(value)
            processSession(deserialize)
        }
    }

    override suspend fun unassign(call: ApplicationCall) {
        call.response.cookies.appendExpired(cookieName)
    }
}

class CookieValueSessionTrackerBuilder<S : Any>(val type: KClass<S>) {
    var settings = SessionCookiesSettings()
    var cookieName: String = "SESSION"
    var serializer: SessionSerializer<S> = autoSerializerOf(type)

    fun build(): SessionTracker<S> = CookieValueSessionTracker(settings, cookieName, serializer)
}

fun <S : Any> SessionConfigBuilder<S>.withCookieByValue(block: CookieValueSessionTrackerBuilder<S>.() -> Unit = {}) {
    sessionTracker = CookieValueSessionTrackerBuilder(sessionType).apply(block).build()
}

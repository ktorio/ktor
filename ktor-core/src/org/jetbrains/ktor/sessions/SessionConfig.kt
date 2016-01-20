package org.jetbrains.ktor.sessions

import kotlin.reflect.*

class SessionConfig<S : Any>(
        val sessionType: KClass<S>,
        val sessionTracker: SessionTracker<S>
)

class SessionConfigBuilder<S : Any>(val sessionType: KClass<S>) {
    var sessionTracker: SessionTracker<S>? = null

    fun build(): SessionConfig<S> {
        val tracker = sessionTracker ?: CookieValueSessionTracker(SessionCookiesSettings(), "SESSION", autoSerializerOf(sessionType))
        return SessionConfig(sessionType, tracker)
    }
}

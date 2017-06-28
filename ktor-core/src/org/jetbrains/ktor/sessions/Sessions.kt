package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.time.*
import java.time.temporal.*
import kotlin.reflect.*


// If SessionData is present in attributes of a call, then session was either create or retrieved
// if value is null, session will be cleared
private data class SessionData(val value: Any?, val state: SessionState, val sessions: Sessions)

enum class SessionState {
    None, Incoming, Modified, Deleted
}

private val SessionKey = AttributeKey<SessionData>("SessionKey")

class Sessions(val tracker: SessionTracker, val settings: SessionCookiesSettings) {
    class Configuration {
        var duration: TemporalAmount = Duration.ofDays(7)
        var requireHttps: Boolean = false
        val transformers: MutableList<SessionCookieTransformer> = mutableListOf()

        var tracker: SessionTracker = SessionTrackerCookieValue(ValuesMap::class, "SESSION", autoSerializerOf<ValuesMap>())
    }

    companion object : ApplicationFeature<ApplicationCallPipeline, Sessions.Configuration, Sessions> {
        override val key = AttributeKey<Sessions>("Sessions")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Sessions {
            val configuration = Sessions.Configuration().apply(configure)
            val settings = SessionCookiesSettings(configuration.duration, configuration.requireHttps, configuration.transformers)
            val sessions = Sessions(configuration.tracker, settings)

            pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
                // lookup current session in the tracker and store it in the call attributes
                val session = sessions.tracker.lookup(this, sessions.settings)
                val state = if (session != null) SessionState.Incoming else SessionState.None
                val sessionData = SessionData(session, state, sessions)
                call.attributes.put(SessionKey, sessionData)
            }

            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Before) {
                val data = call.attributes.getOrNull(SessionKey) ?: throw IllegalStateException("Sessions feature is installed inconsistently")
                when (data.state) {
                    SessionState.None -> {
                        /* do nothing */
                    }
                    SessionState.Incoming -> {
                        /* do nothing */
                    }
                    SessionState.Modified -> {
                        data.value ?: throw IllegalStateException("Session data shouldn't be null in Modified state")
                        sessions.tracker.assign(call, data.value, sessions.settings)
                    }
                    SessionState.Deleted -> {
                        sessions.tracker.unassign(call)
                    }
                }
            }

            return sessions
        }
    }
}

fun ApplicationCall.setSession(value: Any?) {
    val data = attributes.getOrNull(SessionKey) ?: throw IllegalStateException("Sessions feature should be installed to use sessions")
    val state = when {
        value != null -> {
            data.sessions.tracker.validate(value)
            SessionState.Modified
        }
        data.state == SessionState.None -> SessionState.None
        else -> SessionState.Deleted
    }
    attributes.put(SessionKey, data.copy(value = value, state = state))
}

fun ApplicationCall.clearSession() = setSession(null)

fun ApplicationCall.currentSession(): Any? {
    val data = attributes.getOrNull(SessionKey) ?: throw IllegalStateException("Sessions feature should be installed to use sessions")
    return data.value
}

inline fun <reified S : Any> ApplicationCall.currentSessionOf(): S? = currentSessionOf(S::class)
fun <S : Any> ApplicationCall.currentSessionOf(sessionType: KClass<S>): S? {
    return currentSession()?.let {
        require(sessionType.java.isInstance(it)) { "Session instance '${it.javaClass} is not an instance of $sessionType" }
        it.cast(sessionType)
    }
}

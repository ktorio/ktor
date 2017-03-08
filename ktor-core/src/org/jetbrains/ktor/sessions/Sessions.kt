package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*

private val SessionConfigKey = AttributeKey<SessionConfig<*>>("SessionConfig")
private val SessionKey = AttributeKey<Any>("Session")

@Suppress("UNCHECKED_CAST")
private fun <S : Any> ApplicationCall.sessionConfig() = attributes[SessionConfigKey] as SessionConfig<S>

inline fun <reified T : Any> ApplicationCall.session() = session(T::class)

@Suppress("UNCHECKED_CAST")
fun <S : Any> ApplicationCall.session(type: KClass<S>): S = attributes.computeIfAbsent(SessionKey) {
    val config = sessionConfig<S>()
    require(type.java.isAssignableFrom(config.sessionType.java)) { "type $type should be a subtype of ${config.sessionType}" }
    newInstance(config.sessionType)
}.cast(type)

fun <S : Any> ApplicationCall.session(session: S) = session.apply {
    val type = sessionConfig<S>().sessionType
    require(type.java.isInstance(session)) { "Instance should be an instance of $type" }
    attributes.put(SessionKey, session)
}

inline fun <reified S : Any> ApplicationCall.sessionOrNull(): S? = sessionOrNull(S::class)
fun <S : Any> ApplicationCall.sessionOrNull(sessionType: KClass<S>): S? {
    if (SessionKey in attributes) {
        val attribute = attributes[SessionKey]
        val session = attribute.cast(sessionType)
        return session
    }
    return null
}


inline fun <reified S : Any> Pipeline<ApplicationCall>.withSessions(noinline block: SessionConfigBuilder<S>.() -> Unit) =
        withSessions(S::class, block)

inline fun <S : Any> Pipeline<ApplicationCall>.withSessions(type: KClass<S>, block: SessionConfigBuilder<S>.() -> Unit) {
    withSessions(SessionConfigBuilder(type).apply(block).build())
}

fun <S : Any> Pipeline<ApplicationCall>.withSessions(sessionConfig: SessionConfig<S>) {
    intercept(ApplicationCallPipeline.Infrastructure) { call ->
        call.response.pipeline.intercept(ApplicationResponsePipeline.Before) {
            if (call.attributes.contains(SessionKey)) {
                val session = call.sessionOrNull(sessionConfig.sessionType)
                if (session != null) {
                    sessionConfig.sessionTracker.assign(call, session)
                }
            }
        }

        call.attributes.put(SessionConfigKey, sessionConfig)
        sessionConfig.sessionTracker.lookup(this) {
            call.attributes.put(SessionKey, it)
        }
    }
}

suspend fun ApplicationCall.clearSession() {
    val sessionConfig = attributes.getOrNull(SessionConfigKey)

    if (sessionConfig != null) {
        sessionConfig.sessionTracker.unassign(this)
    }

    attributes.remove(SessionKey)
}
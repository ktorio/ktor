package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import kotlin.reflect.*

@Deprecated("Use sessions.get method", ReplaceWith("sessions.get<S>()"), DeprecationLevel.ERROR)
inline fun <reified S : Any> ApplicationCall.session() = sessions.get<S>()!!

@Deprecated("Use sessions.get method", ReplaceWith("sessions.get<S>()"), DeprecationLevel.ERROR)
inline fun <reified S : Any> ApplicationCall.session(type: KClass<S>): S = sessions.get<S>()!!

@Deprecated("Use sessions.set method", ReplaceWith("sessions.set(session)"), DeprecationLevel.ERROR)
inline fun <reified S : Any> ApplicationCall.session(session: S) = sessions.set(session)

@Deprecated("Use sessions.get method", ReplaceWith("sessions.get<S>()"), DeprecationLevel.ERROR)
inline fun <reified S : Any> ApplicationCall.sessionOrNull(): S? = sessions.get<S>()

@Deprecated("Use install(Sessions) instead", level = DeprecationLevel.ERROR)
inline fun <reified S : Any> ApplicationCallPipeline.withSessions(noinline block: () -> Unit): Nothing = TODO()

package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.routing.*
import kotlin.reflect.*

@JvmName("withSessionsForRoutes")
fun <S : Any> InterceptApplicationCall<RoutingApplicationCall>.withSessions(type: KClass<S>, block: SessionConfigBuilder<S>.() -> Unit) {
    @Suppress("UNCHECKED_CAST")
    (this as InterceptApplicationCall<ApplicationCall>).withSessions(type, block)
}


@JvmName("withSessionsForRoutes")
inline fun <reified S : Any> InterceptApplicationCall<RoutingApplicationCall>.withSessions(noinline block: SessionConfigBuilder<S>.() -> Unit) {
    withSessions(S::class, block)
}


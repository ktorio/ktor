package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*
import kotlin.reflect.*

private val SessionConfigKey = AttributeKey<SessionConfig<*>>()
private val SessionKey = AttributeKey<Any>()

class SessionConfig<T: Any>(
        val type: KClass<T>,
        val serializer: SessionSerializer<T> = autoSerializerOf(type),
        val storage: SessionStorage,
        val exec: ScheduledExecutorService = Executors.unconfigurableScheduledExecutorService(Executors.newScheduledThreadPool(1)),
        val activateSession: (T) -> Unit = {},
        val passivateSession: (T) -> Unit = {},
        val sessionCookieName: String = "SESSION"
)

fun <T : Any> ApplicationRequestContext.session(session: T) = session.apply {
    val type = attributes[SessionConfigKey].type
    require(type.java.isInstance(session)) { "Instance should be an instance of $type" }
    attributes.put(SessionKey, session)
}

inline fun <reified T : Any> ApplicationRequestContext.session() = session(T::class)

@Suppress("UNCHECKED_CAST")
fun <T : Any> ApplicationRequestContext.session(type: KClass<T>): T = attributes.computeIfAbsent(SessionKey) {
    val config = attributes[SessionConfigKey]
    require(type.java.isAssignableFrom(config.type.java)) { "type $type should be a subtype of ${config.type}" }
    val ctor = config.type.constructors.firstOrNull { it.parameters.isEmpty() } ?: throw IllegalArgumentException("Type ${config.type} should have no-arg constructor or use session(T) instead")
    ctor.call()
}.cast(type)

fun <T : Any> Application.withSessions(sessionConfig: SessionConfig<T>) {
    intercept { next ->
        attributes.put(SessionConfigKey, sessionConfig)
        val sessionCookie = request.cookies[sessionConfig.sessionCookieName]

        if (sessionCookie == null) {
            next()
        } else {
            handleAsync(sessionConfig.exec, {

                next()
            }, {
            })
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> Any.cast(type: KClass<T>) = if (type.java.isInstance(this)) this as T else throw ClassCastException("$javaClass couldn't be cast to $type")
private inline fun <reified T : Any> Any.cast() = cast(T::class)
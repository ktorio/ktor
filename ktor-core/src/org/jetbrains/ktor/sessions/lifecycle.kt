package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*

private val SessionConfigKey = AttributeKey<SessionConfig<*>>()
private val SessionKey = AttributeKey<Any>()

@Suppress("UNCHECKED_CAST")
private fun <S : Any> ApplicationRequestContext.sessionConfig() = attributes[SessionConfigKey] as SessionConfig<S>

inline fun <reified T : Any> ApplicationRequestContext.session() = session(T::class)

fun <S : Any> ApplicationRequestContext.session(session: S) = session.apply {
    val type = sessionConfig<S>().type
    require(type.java.isInstance(session)) { "Instance should be an instance of $type" }
    attributes.put(SessionKey, session)
}

@Suppress("UNCHECKED_CAST")
fun <S : Any> ApplicationRequestContext.session(type: KClass<S>): S = attributes.computeIfAbsent(SessionKey) {
    val config = sessionConfig<S>()
    require(type.java.isAssignableFrom(config.type.java)) { "type $type should be a subtype of ${config.type}" }
    newInstance(config.type)
}.cast(type)

inline fun <reified S : Any> ApplicationRequestContext.sessionOrNull(): S? = sessionOrNull(S::class)
fun <S : Any> ApplicationRequestContext.sessionOrNull(type: KClass<S>): S? = if (SessionKey in attributes) attributes[SessionKey].cast(type) else null

inline fun <reified S : Any> InterceptableWithContext<ApplicationRequestContext>.withSessions(noinline block: SessionConfigBuilder<S>.() -> Unit) =
    withSessions(S::class, block)

fun <S : Any> InterceptableWithContext<ApplicationRequestContext>.withSessions(type: KClass<S>, block: SessionConfigBuilder<S>.() -> Unit) {
    val sessionConfig = with(SessionConfigBuilder(type)) {
        block()
        build()
    }

    intercept { next ->
        attributes.put(SessionConfigKey, sessionConfig)

        sessionConfig.sessionTracker.lookup(this, {
            attributes.put(SessionKey, it)
        }) {
            next().apply {
                if (attributes.contains(SessionKey)) {
                    val session = sessionOrNull(sessionConfig.type)
                    if (session != null) {
                        sessionConfig.sessionTracker.assign(this@intercept, session)
                    }
                }
            }
        }
    }
}

class SessionConfigBuilder<S : Any>(override val type: KClass<S>) : HasTracker<S> {
    override var sessionTracker: SessionTracker<S> = CookieByValueSessionTracker(CookiesSettings(), "SESSION", autoSerializerOf(type))

    fun build() = SessionConfig(type, sessionTracker)
}

@Suppress("UNCHECKED_CAST")
private fun <T : Any> Any.cast(type: KClass<T>) = if (type.java.isInstance(this)) this as T else throw ClassCastException("$javaClass couldn't be cast to $type")

private inline fun <reified T : Any> Any.cast() = cast(T::class)
private fun <T : Any> newInstance(type: KClass<T>): T =
        type.constructors.firstOrNull { it.parameters.isEmpty() }?.call() ?: throw IllegalArgumentException("Type $type should have no-arg constructor or use session(T) instead")

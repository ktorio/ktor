package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import kotlin.reflect.*

fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>) {
    cookie(name, sessionType, {})
}

inline fun <reified S : Any> Sessions.Configuration.cookie(name: String): Unit {
    cookie<S>(name, {})
}

inline fun <reified S : Any> Sessions.Configuration.cookie(name: String, block: CookieValueSessionBuilder<S>.() -> Unit) {
    cookie(name, S::class, block)
}

inline fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>, block: CookieValueSessionBuilder<S>.() -> Unit) {
    val builder = CookieValueSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportCookie(name, transformers = builder.transformers)
    val tracker = SessionTrackerByValue(sessionType, builder.serializer)
    val registration = SessionProvider(name, sessionType, transport, tracker)
    register(registration)
}

class SessionTrackerByValue(val type: KClass<*>, val serializer: SessionSerializer) : SessionTracker {
    suspend override fun load(call: ApplicationCall, transport: String?): Any? {
        return transport?.let { serializer.deserialize(it) }
    }

    override suspend fun store(call: ApplicationCall, value: Any): String {
        val serialized = serializer.serialize(value)
        return serialized
    }

    override fun validate(value: Any) {
        if (!type.javaObjectType.isAssignableFrom(value.javaClass)) {
            throw IllegalArgumentException("Value for this session tracker expected to be of type $type but was $value")
        }
    }

    suspend override fun clear(call: ApplicationCall) {
        // it's stateless, so nothing to clear
    }
}

class CookieValueSessionBuilder<S : Any>(val type: KClass<S>) {
    var serializer: SessionSerializer = autoSerializerOf(type)
    val transformers = mutableListOf<SessionTransportTransformer>()
    fun transform(transformer: SessionTransportTransformer) {
        transformers.add(transformer)
    }
}

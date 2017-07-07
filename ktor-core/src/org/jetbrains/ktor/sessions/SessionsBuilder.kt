package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.util.*
import kotlin.reflect.*

// cookie by id
fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>, storage: SessionStorage) = cookie(name, sessionType, storage, {})

inline fun <reified S : Any> Sessions.Configuration.cookie(name: String, storage: SessionStorage): Unit = cookie(name, S::class, storage, {})
inline fun <reified S : Any> Sessions.Configuration.cookie(name: String, storage: SessionStorage, block: CookieIdSessionBuilder<S>.() -> Unit) = cookie(name, S::class, storage, block)

inline fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>, storage: SessionStorage, block: CookieIdSessionBuilder<S>.() -> Unit) {
    val builder = CookieIdSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportCookie(name, builder.cookie, builder.transformers)
    val tracker = SessionTrackerById(sessionType, builder.serializer, storage, builder.sessionIdProvider)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

// header by id
fun <S : Any> Sessions.Configuration.header(name: String, sessionType: KClass<S>, storage: SessionStorage) = header(name, sessionType, storage, {})

inline fun <reified S : Any> Sessions.Configuration.header(name: String, storage: SessionStorage): Unit = header(name, S::class, storage, {})
inline fun <reified S : Any> Sessions.Configuration.header(name: String, storage: SessionStorage, block: HeaderIdSessionBuilder<S>.() -> Unit) = header(name, S::class, storage, block)

inline fun <S : Any> Sessions.Configuration.header(name: String, sessionType: KClass<S>, storage: SessionStorage, block: HeaderIdSessionBuilder<S>.() -> Unit) {
    val builder = HeaderIdSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportHeader(name, builder.transformers)
    val tracker = SessionTrackerById(sessionType, builder.serializer, storage, builder.sessionIdProvider)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

// cookie by value
fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>): Unit = cookie(name, sessionType, {})

inline fun <reified S : Any> Sessions.Configuration.cookie(name: String): Unit = cookie(name, S::class, {})
inline fun <reified S : Any> Sessions.Configuration.cookie(name: String, block: CookieSessionBuilder<S>.() -> Unit): Unit = cookie(name, S::class, block)

inline fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>, block: CookieSessionBuilder<S>.() -> Unit) {
    val builder = CookieSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportCookie(name, builder.cookie, builder.transformers)
    val tracker = SessionTrackerByValue(sessionType, builder.serializer)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

// header by value
fun <S : Any> Sessions.Configuration.header(name: String, sessionType: KClass<S>): Unit = header(name, sessionType, {})

inline fun <reified S : Any> Sessions.Configuration.header(name: String): Unit = header(name, S::class, {})
inline fun <reified S : Any> Sessions.Configuration.header(name: String, block: HeaderSessionBuilder<S>.() -> Unit): Unit = header(name, S::class, block)

inline fun <S : Any> Sessions.Configuration.header(name: String, sessionType: KClass<S>, block: HeaderSessionBuilder<S>.() -> Unit) {
    val builder = HeaderSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportHeader(name, builder.transformers)
    val tracker = SessionTrackerByValue(sessionType, builder.serializer)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

class CookieIdSessionBuilder<S : Any>(type: KClass<S>) : CookieSessionBuilder<S>(type) {
    fun identity(f: () -> String) {
        sessionIdProvider = f
    }

    var sessionIdProvider: () -> String = { nextNonce() }
        private set
}

open class CookieSessionBuilder<S : Any>(val type: KClass<S>) {
    var serializer: SessionSerializer = autoSerializerOf(type)

    private val _transformers = mutableListOf<SessionTransportTransformer>()
    val transformers: List<SessionTransportTransformer> get() = _transformers
    fun transform(transformer: SessionTransportTransformer) {
        _transformers.add(transformer)
    }

    val cookie = CookieConfiguration()
    var requireHttps: Boolean = false
}

open class HeaderSessionBuilder<S : Any>(val type: KClass<S>) {
    var serializer: SessionSerializer = autoSerializerOf(type)

    private val _transformers = mutableListOf<SessionTransportTransformer>()
    val transformers: List<SessionTransportTransformer> get() = _transformers
    fun transform(transformer: SessionTransportTransformer) {
        _transformers.add(transformer)
    }
}

class HeaderIdSessionBuilder<S : Any>(type: KClass<S>) : HeaderSessionBuilder<S>(type) {
    fun identity(f: () -> String) {
        sessionIdProvider = f
    }

    var sessionIdProvider: () -> String = { nextNonce() }
        private set
}

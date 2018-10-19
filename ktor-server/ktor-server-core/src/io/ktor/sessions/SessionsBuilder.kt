package io.ktor.sessions

import io.ktor.util.*
import kotlin.reflect.*

/**
 * Configure sessions to get it from cookie using session [storage]
 */
fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>, storage: SessionStorage): Unit =
    cookie(name, sessionType, storage, {})

/**
 * Configure sessions to get it from cookie using session [storage]
 */
inline fun <reified S : Any> Sessions.Configuration.cookie(name: String, storage: SessionStorage): Unit =
    cookie(name, S::class, storage, {})

/**
 * Configures a session using a cookie with the specified [name] using it as a session id.
 * The actual content of the session is stored at server side using the specified [storage].
 * The cookie configuration can be set inside [block] using the cookie property exposed by [CookieIdSessionBuilder].
 */
inline fun <reified S : Any> Sessions.Configuration.cookie(
    name: String,
    storage: SessionStorage,
    block: CookieIdSessionBuilder<S>.() -> Unit
) = cookie(name, S::class, storage, block)

/**
 * Configure sessions to get it from cookie using session [storage]
 */
inline fun <S : Any> Sessions.Configuration.cookie(
    name: String,
    sessionType: KClass<S>,
    storage: SessionStorage,
    block: CookieIdSessionBuilder<S>.() -> Unit
) {
    val builder = CookieIdSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportCookie(name, builder.cookie, builder.transformers)
    val tracker = SessionTrackerById(sessionType, builder.serializer, storage, builder.sessionIdProvider)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

// header by id
/**
 * Configure sessions to get it from HTTP header using session [storage]
 */
fun <S : Any> Sessions.Configuration.header(name: String, sessionType: KClass<S>, storage: SessionStorage) =
    header(name, sessionType, storage, {})

/**
 * Configure sessions to get it from HTTP header using session [storage]
 */
inline fun <reified S : Any> Sessions.Configuration.header(name: String, storage: SessionStorage): Unit =
    header(name, S::class, storage, {})

/**
 * Configures a session using a header with the specified [name] using it as a session id.
 * The actual content of the session is stored at server side using the specified [storage].
 */
inline fun <reified S : Any> Sessions.Configuration.header(
    name: String,
    storage: SessionStorage,
    block: HeaderIdSessionBuilder<S>.() -> Unit
) = header(name, S::class, storage, block)

/**
 * Configures a session using a header with the specified [name] using it as a session id.
 * The actual content of the session is stored at server side using the specified [storage].
 */
inline fun <S : Any> Sessions.Configuration.header(
    name: String,
    sessionType: KClass<S>,
    storage: SessionStorage,
    block: HeaderIdSessionBuilder<S>.() -> Unit
) {
    val builder = HeaderIdSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportHeader(name, builder.transformers)
    val tracker = SessionTrackerById(sessionType, builder.serializer, storage, builder.sessionIdProvider)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

// cookie by value
/**
 * Configure sessions to serialize to/from HTTP cookie
 */
fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>): Unit = cookie(name, sessionType, {})

/**
 * Configure sessions to serialize to/from HTTP cookie
 */
inline fun <reified S : Any> Sessions.Configuration.cookie(name: String): Unit = cookie(name, S::class, {})

/**
 * Configures a session using a cookie with the specified [name] using it as for the actual session content
 * optionally transformed by specified transforms in [block].
 * The cookie configuration can be set inside [block] using the cookie property exposed by [CookieIdSessionBuilder].
 */
inline fun <reified S : Any> Sessions.Configuration.cookie(
    name: String,
    block: CookieSessionBuilder<S>.() -> Unit
): Unit = cookie(name, S::class, block)

/**
 * Configure sessions to serialize to/from HTTP cookie configuring it by [block]
 */
inline fun <S : Any> Sessions.Configuration.cookie(
    name: String,
    sessionType: KClass<S>,
    block: CookieSessionBuilder<S>.() -> Unit
) {
    val builder = CookieSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportCookie(name, builder.cookie, builder.transformers)
    val tracker = SessionTrackerByValue(sessionType, builder.serializer)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

// header by value
/**
 * Configure sessions to serialize to/from HTTP header
 */
fun <S : Any> Sessions.Configuration.header(name: String, sessionType: KClass<S>): Unit = header(name, sessionType, {})

/**
 * Configure sessions to serialize to/from HTTP header
 */
inline fun <reified S : Any> Sessions.Configuration.header(name: String): Unit = header(name, S::class, {})

/**
 * Configures a session using a header with the specified [name] using it for the actual session content
 * optionally transformed by specified transforms in [block].
 */
inline fun <reified S : Any> Sessions.Configuration.header(
    name: String,
    block: HeaderSessionBuilder<S>.() -> Unit
): Unit = header(name, S::class, block)

/**
 * Configures a session using a header with the specified [name] using it for the actual session content
 * and apply [block] function to configure serializataion and optional transformations
 */
inline fun <S : Any> Sessions.Configuration.header(
    name: String,
    sessionType: KClass<S>,
    block: HeaderSessionBuilder<S>.() -> Unit
) {
    val builder = HeaderSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportHeader(name, builder.transformers)
    val tracker = SessionTrackerByValue(sessionType, builder.serializer)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

/**
 * Cookie session configuration builder
 */
class CookieIdSessionBuilder<S : Any>(type: KClass<S>) : CookieSessionBuilder<S>(type) {
    /**
     * Register session ID generation function
     */
    @KtorExperimentalAPI
    fun identity(f: () -> String) {
        sessionIdProvider = f
    }

    /**
     * Current session ID provider function
     */
    var sessionIdProvider: () -> String = { generateNonce() }
        private set
}

/**
 * Cookie session configuration builder
 * @property type - session instance type
 */
open class CookieSessionBuilder<S : Any>(val type: KClass<S>) {
    /**
     * Session instance serializer
     */
    var serializer: SessionSerializer = autoSerializerOf(type)

    private val _transformers = mutableListOf<SessionTransportTransformer>()

    /**
     * Registered session transformers
     */
    val transformers: List<SessionTransportTransformer> get() = _transformers

    /**
     * Register a session [transformer]. Useful for encryption, signing and so on
     */
    fun transform(transformer: SessionTransportTransformer) {
        _transformers.add(transformer)
    }

    /**
     * Cookie header configuration
     */
    val cookie = CookieConfiguration()
}

/**
 * Header session configuration builder
 * @property type session instance type
 */
open class HeaderSessionBuilder<S : Any>(val type: KClass<S>) {
    /**
     * Session instance serializer
     */
    var serializer: SessionSerializer = autoSerializerOf(type)

    private val _transformers = mutableListOf<SessionTransportTransformer>()

    /**
    * Registered session transformers
    */
    val transformers: List<SessionTransportTransformer> get() = _transformers

    /**
     * Register a session [transformer]. Useful for encryption, signing and so on
     */
    fun transform(transformer: SessionTransportTransformer) {
        _transformers.add(transformer)
    }
}

/**
 * Header session configuration builder
 */
class HeaderIdSessionBuilder<S : Any>(type: KClass<S>) : HeaderSessionBuilder<S>(type) {
    /**
     * Register session ID generation function
     */
    fun identity(f: () -> String) {
        sessionIdProvider = f
    }

    /**
     * Current session ID provider function
     */
    var sessionIdProvider: () -> String = { generateNonce() }
        private set
}

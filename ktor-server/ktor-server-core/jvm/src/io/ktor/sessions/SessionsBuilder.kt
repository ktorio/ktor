/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.sessions

import io.ktor.util.*
import kotlin.reflect.*

/**
 * Configure sessions to get it from cookie using session [storage]
 */
@Deprecated("Use reified types instead.")
public fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>, storage: SessionStorage) {
    @Suppress("DEPRECATION")
    val builder = CookieIdSessionBuilder(sessionType)
    cookie(name, builder, sessionType, storage)
}

/**
 * Configure sessions to get it from cookie using session [storage]
 */
public inline fun <reified S : Any> Sessions.Configuration.cookie(name: String, storage: SessionStorage) {
    val sessionType = S::class

    @Suppress("DEPRECATION")
    val builder = CookieIdSessionBuilder(sessionType)
    cookie(name, builder, sessionType, storage)
}

@PublishedApi
internal fun <S : Any> Sessions.Configuration.cookie(
    name: String,
    builder: CookieIdSessionBuilder<S>,
    sessionType: KClass<S>,
    storage: SessionStorage
) {
    val transport = SessionTransportCookie(name, builder.cookie, builder.transformers)
    val tracker = SessionTrackerById(sessionType, builder.serializer, storage, builder.sessionIdProvider)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

/**
 * Configures a session using a cookie with the specified [name] using it as a session id.
 * The actual content of the session is stored at server side using the specified [storage].
 * The cookie configuration can be set inside [block] using the cookie property exposed by [CookieIdSessionBuilder].
 */
public inline fun <reified S : Any> Sessions.Configuration.cookie(
    name: String,
    storage: SessionStorage,
    block: CookieIdSessionBuilder<S>.() -> Unit
) {
    val sessionType = S::class

    @Suppress("DEPRECATION")
    val builder = CookieIdSessionBuilder(sessionType).apply(block)
    cookie(name, builder, sessionType, storage)
}

/**
 * Configure sessions to get it from cookie using session [storage]
 */
@Deprecated("Use reified types intead.")
public inline fun <S : Any> Sessions.Configuration.cookie(
    name: String,
    sessionType: KClass<S>,
    storage: SessionStorage,
    block: CookieIdSessionBuilder<S>.() -> Unit
) {
    @Suppress("DEPRECATION")
    val builder = CookieIdSessionBuilder(sessionType).apply(block)
    cookie(name, builder, sessionType, storage)
}

// header by id
/**
 * Configure sessions to get it from HTTP header using session [storage]
 */
@Deprecated("Use reified type instead.")
public fun <S : Any> Sessions.Configuration.header(name: String, sessionType: KClass<S>, storage: SessionStorage) {
    @Suppress("DEPRECATION")
    val builder = HeaderIdSessionBuilder(sessionType)
    header(name, sessionType, storage, builder)
}

/**
 * Configure sessions to get it from HTTP header using session [storage]
 */
public inline fun <reified S : Any> Sessions.Configuration.header(name: String, storage: SessionStorage) {
    header<S>(name, storage, {})
}

/**
 * Configures a session using a header with the specified [name] using it as a session id.
 * The actual content of the session is stored at server side using the specified [storage].
 */
public inline fun <reified S : Any> Sessions.Configuration.header(
    name: String,
    storage: SessionStorage,
    block: HeaderIdSessionBuilder<S>.() -> Unit
) {
    val sessionType = S::class

    @Suppress("DEPRECATION")
    val builder = HeaderIdSessionBuilder(sessionType).apply(block)
    header(name, sessionType, storage, builder)
}

/**
 * Configures a session using a header with the specified [name] using it as a session id.
 * The actual content of the session is stored at server side using the specified [storage].
 */
@Deprecated("Use reified types instead.")
public inline fun <S : Any> Sessions.Configuration.header(
    name: String,
    sessionType: KClass<S>,
    storage: SessionStorage,
    block: HeaderIdSessionBuilder<S>.() -> Unit
) {
    @Suppress("DEPRECATION")
    val builder = HeaderIdSessionBuilder(sessionType).apply(block)
    header(name, sessionType, storage, builder)
}

@PublishedApi
internal fun <S : Any> Sessions.Configuration.header(
    name: String,
    sessionType: KClass<S>,
    storage: SessionStorage?,
    builder: HeaderSessionBuilder<S>
) {
    val transport = SessionTransportHeader(name, builder.transformers)
    val tracker = when {
        storage != null && builder is HeaderIdSessionBuilder<S> -> SessionTrackerById(
            sessionType,
            builder.serializer,
            storage,
            builder.sessionIdProvider
        )
        else -> SessionTrackerByValue(sessionType, builder.serializer)
    }
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

// cookie by value
/**
 * Configure sessions to serialize to/from HTTP cookie
 */
@Deprecated("Use reified type parameter instead.")
public fun <S : Any> Sessions.Configuration.cookie(name: String, sessionType: KClass<S>) {
    @Suppress("DEPRECATION")
    val builder = CookieSessionBuilder(sessionType)
    cookie(name, sessionType, builder)
}

/**
 * Configure sessions to serialize to/from HTTP cookie
 */
public inline fun <reified S : Any> Sessions.Configuration.cookie(name: String) {
    val sessionType = S::class

    @Suppress("DEPRECATION")
    val builder = CookieSessionBuilder(sessionType)
    cookie(name, sessionType, builder)
}

/**
 * Configures a session using a cookie with the specified [name] using it as for the actual session content
 * optionally transformed by specified transforms in [block].
 * The cookie configuration can be set inside [block] using the cookie property exposed by [CookieIdSessionBuilder].
 */
public inline fun <reified S : Any> Sessions.Configuration.cookie(
    name: String,
    block: CookieSessionBuilder<S>.() -> Unit
) {
    val sessionType = S::class

    @Suppress("DEPRECATION")
    val builder = CookieSessionBuilder(sessionType).apply(block)
    cookie(name, sessionType, builder)
}

/**
 * Configure sessions to serialize to/from HTTP cookie configuring it by [block]
 */
@Deprecated("Use reified type instead.")
public inline fun <S : Any> Sessions.Configuration.cookie(
    name: String,
    sessionType: KClass<S>,
    block: CookieSessionBuilder<S>.() -> Unit
) {
    @Suppress("DEPRECATION")
    val builder = CookieSessionBuilder(sessionType).apply(block)
    cookie(name, sessionType, builder)
}

@PublishedApi
internal fun <S : Any> Sessions.Configuration.cookie(
    name: String,
    sessionType: KClass<S>,
    builder: CookieSessionBuilder<S>
) {
    val transport = SessionTransportCookie(name, builder.cookie, builder.transformers)
    val tracker = SessionTrackerByValue(sessionType, builder.serializer)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

// header by value
/**
 * Configure sessions to serialize to/from HTTP header
 */
@Deprecated("Use reified type instead.")
public fun <S : Any> Sessions.Configuration.header(name: String, sessionType: KClass<S>) {
    @Suppress("DEPRECATION")
    val builder = HeaderSessionBuilder(sessionType)
    header(name, sessionType, null, builder)
}

/**
 * Configure sessions to serialize to/from HTTP header
 */
public inline fun <reified S : Any> Sessions.Configuration.header(name: String) {
    header<S>(name, {})
}

/**
 * Configures a session using a header with the specified [name] using it for the actual session content
 * optionally transformed by specified transforms in [block].
 */
public inline fun <reified S : Any> Sessions.Configuration.header(
    name: String,
    block: HeaderSessionBuilder<S>.() -> Unit
) {
    val sessionType = S::class

    @Suppress("DEPRECATION")
    val builder = HeaderSessionBuilder(sessionType).apply(block)
    header(name, sessionType, null, builder)
}

/**
 * Configures a session using a header with the specified [name] using it for the actual session content
 * and apply [block] function to configure serializataion and optional transformations
 */
@Deprecated("Use reified type instead.")
public inline fun <S : Any> Sessions.Configuration.header(
    name: String,
    sessionType: KClass<S>,
    block: HeaderSessionBuilder<S>.() -> Unit
) {
    @Suppress("DEPRECATION")
    val builder = HeaderSessionBuilder(sessionType).apply(block)
    val transport = SessionTransportHeader(name, builder.transformers)
    val tracker = SessionTrackerByValue(sessionType, builder.serializer)
    val provider = SessionProvider(name, sessionType, transport, tracker)
    register(provider)
}

/**
 * Cookie session configuration builder
 */
@Suppress("DEPRECATION")
public class CookieIdSessionBuilder<S : Any> @Deprecated("Use builder functions instead.") constructor(type: KClass<S>) :
    CookieSessionBuilder<S>(type) {
    /**
     * Register session ID generation function
     */
    public fun identity(f: () -> String) {
        sessionIdProvider = f
    }

    /**
     * Current session ID provider function
     */
    public var sessionIdProvider: () -> String = { generateNonce() }
        private set
}

/**
 * Cookie session configuration builder
 * @property type - session instance type
 */
public open class CookieSessionBuilder<S : Any> @Deprecated("Use builder functions instead.") constructor(
    public val type: KClass<S>
) {
    /**
     * Session instance serializer
     */
    public var serializer: SessionSerializer<S> = @Suppress("DEPRECATION") SessionSerializerReflection(type)

    private val _transformers = mutableListOf<SessionTransportTransformer>()

    /**
     * Registered session transformers
     */
    public val transformers: List<SessionTransportTransformer> get() = _transformers

    /**
     * Register a session [transformer]. Useful for encryption, signing and so on
     */
    public fun transform(transformer: SessionTransportTransformer) {
        _transformers.add(transformer)
    }

    /**
     * Cookie header configuration
     */
    public val cookie: CookieConfiguration = CookieConfiguration()
}

/**
 * Header session configuration builder
 * @property type session instance type
 */
public open class HeaderSessionBuilder<S : Any> @Deprecated("Use builder functions instead.") constructor(
    public val type: KClass<S>
) {
    /**
     * Session instance serializer
     */
    public var serializer: SessionSerializer<S> = @Suppress("DEPRECATION") SessionSerializerReflection(type)

    private val _transformers = mutableListOf<SessionTransportTransformer>()

    /**
     * Registered session transformers
     */
    public val transformers: List<SessionTransportTransformer> get() = _transformers

    /**
     * Register a session [transformer]. Useful for encryption, signing and so on
     */
    public fun transform(transformer: SessionTransportTransformer) {
        _transformers.add(transformer)
    }
}

/**
 * Header session configuration builder
 */
@Suppress("DEPRECATION")
public class HeaderIdSessionBuilder<S : Any> @Deprecated("Use builder functions instead.") constructor(type: KClass<S>) :
    @Suppress("DEPRECATION") HeaderSessionBuilder<S>(type) {
    /**
     * Register session ID generation function
     */
    public fun identity(f: () -> String) {
        sessionIdProvider = f
    }

    /**
     * Current session ID provider function
     */
    public var sessionIdProvider: () -> String = { generateNonce() }
        private set
}

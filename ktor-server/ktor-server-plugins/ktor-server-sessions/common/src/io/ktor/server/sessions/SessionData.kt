/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.server.application.*
import io.ktor.util.*
import kotlin.reflect.*

/**
 * Gets a current session or fails if the [Sessions] plugin is not installed.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.sessions)
 *
 * @throws MissingApplicationPluginException
 */
public val ApplicationCall.sessions: CurrentSession
    get() = attributes.getOrNull(SessionDataKey) ?: reportMissingSession()

/**
 * A container for all session instances.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.CurrentSession)
 */
public interface CurrentSession {
    /**
     * Sets a new session instance with [name].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.CurrentSession.set)
     *
     * @throws IllegalStateException if no session provider is registered with for [name]
     */
    public fun set(name: String, value: Any?)

    /**
     * Gets a session instance for [name]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.CurrentSession.get)
     *
     * @throws IllegalStateException if no session provider is registered with for [name]
     */
    public fun get(name: String): Any?

    /**
     * Clears a session instance for [name].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.CurrentSession.clear)
     *
     * @throws IllegalStateException if no session provider is registered with for [name]
     */
    public fun clear(name: String)

    /**
     * Clears a session with the specified [sessionId] for the session [name].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.CurrentSession.clear)
     *
     * @param name the session name
     * @param sessionId the session ID to invalidate
     * @throws IllegalStateException if no session provider is registered with for [name]
     * or the session provider doesn't use session IDs
     * @throws UnsupportedOperationException if the session provider doesn't support clearing by ID
     */
    public suspend fun clear(name: String, sessionId: String) {
        throw UnsupportedOperationException("Session clearing is not supported by this session provider")
    }

    /**
     * Finds a session name for the specified [type] or fails if it's not found.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.CurrentSession.findName)
     *
     * @throws IllegalStateException if no session provider registered for [type]
     */
    public fun findName(type: KClass<*>): String
}

/**
 * Extends [CurrentSession] with a call to include session data in the server response.
 */
internal interface StatefulSession : CurrentSession {

    /**
     * Iterates over session data items and writes them to the application call.
     * The session cannot be modified after this is called.
     * This is called after the session data is sent to the response.
     */
    suspend fun sendSessionData(call: ApplicationCall, onEach: (String) -> Unit = {})
}

/**
 * Sets a session instance with the type [T].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.set)
 *
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public inline fun <reified T : Any> CurrentSession.set(value: T?): Unit = set(value, T::class)

/**
 * Sets a session instance with the type [T].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.set)
 *
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public fun <T : Any> CurrentSession.set(value: T?, klass: KClass<T>): Unit = set(findName(klass), value)

/**
 * Gets a session instance with the type [T].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.get)
 *
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public inline fun <reified T : Any> CurrentSession.get(): T? = get(T::class)

/**
 * Gets a session instance with the type [T].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.get)
 *
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
@Suppress("UNCHECKED_CAST")
public fun <T : Any> CurrentSession.get(klass: KClass<T>): T? = get(findName(klass)) as T?

/**
 * Clears a session instance with the type [T].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.clear)
 *
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public inline fun <reified T : Any> CurrentSession.clear(): Unit = clear(T::class)

/**
 * Clears a session instance with the type [T].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.clear)
 *
 * @throws IllegalStateException if no session provider is registered for the type [T]
 */
public fun <T : Any> CurrentSession.clear(klass: KClass<T>): Unit = clear(findName(klass))

/**
 * Clears a session with the specified [sessionId] for the session type [T].
 * This method allows clearing sessions by ID without needing the session instance.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.clear)
 *
 * @param sessionId the session ID to invalidate
 * @throws IllegalStateException if no session provider is registered for the type [T]
 * or the session provider for type [T] doesn't use session IDs
 */
public suspend inline fun <reified T : Any> CurrentSession.clear(sessionId: String): Unit =
    clear(T::class, sessionId)

/**
 * Clears a session with the specified [sessionId] for the session type [klass].
 * This method allows clearing sessions by ID without needing the session instance.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.clear)
 *
 * @param klass the session class type
 * @param sessionId the session ID to invalidate
 * @throws IllegalStateException if no session provider is registered for the type [klass]
 * or the session provider for type [klass] doesn't use session IDs
 */
public suspend fun <T : Any> CurrentSession.clear(klass: KClass<T>, sessionId: String): Unit =
    clear(findName(klass), sessionId)

/**
 * Gets or generates a new session instance using [generator] with the type [T] (or [name] if specified)
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.getOrSet)
 *
 * @throws IllegalStateException if no session provider is registered for the type [T] (or [name] if specified)
 */
public inline fun <reified T : Any> CurrentSession.getOrSet(name: String = findName(T::class), generator: () -> T): T {
    val result = get<T>()

    if (result != null) {
        return result
    }

    return generator().apply {
        set(name, this)
    }
}

internal data class SessionData(
    val providerData: Map<String, SessionProviderData<*>>
) : StatefulSession {

    private var committed = false

    override suspend fun sendSessionData(call: ApplicationCall, onEach: (String) -> Unit) {
        providerData.values.forEach { data ->
            onEach(data.provider.name)
            data.sendSessionData(call)
        }
        committed = true
    }

    override fun findName(type: KClass<*>): String {
        val entry = providerData.entries.firstOrNull { it.value.provider.type == type }
            ?: throw IllegalArgumentException("Session data for type `$type` was not registered")
        return entry.value.provider.name
    }

    override fun set(name: String, value: Any?) {
        if (committed) {
            throw TooLateSessionSetException()
        }
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        setTyped(providerData, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <S : Any> setTyped(data: SessionProviderData<S>, value: Any?) {
        if (value != null) {
            data.provider.tracker.validate(value as S)
        }
        data.newValue = value as S
    }

    override fun get(name: String): Any? {
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        return providerData.newValue ?: providerData.oldValue
    }

    override fun clear(name: String) {
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        providerData.oldValue = null
        providerData.newValue = null
    }

    override suspend fun clear(name: String, sessionId: String) {
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        val tracker = providerData.provider.tracker as? SessionTrackerById
            ?: throw IllegalStateException("Session provider `$name` doesn't use session IDs")
        tracker.clearById(sessionId)
    }
}

internal suspend fun <S : Any> SessionProvider<S>.receiveSessionData(call: ApplicationCall): SessionProviderData<S> {
    val receivedValue = transport.receive(call)
    val unwrapped = tracker.load(call, receivedValue)
    val incoming = receivedValue != null
    return SessionProviderData(unwrapped, null, incoming, this)
}

internal suspend fun <S : Any> SessionProviderData<S>.sendSessionData(call: ApplicationCall) {
    val oldValue = oldValue
    val newValue = newValue
    when {
        newValue != null -> {
            val wrapped = provider.tracker.store(call, newValue)
            provider.transport.send(call, wrapped)
        }

        incoming && oldValue == null -> {
            // Deleted session should be cleared off
            provider.transport.clear(call)
            provider.tracker.clear(call)
        }
    }
}

internal data class SessionProviderData<S : Any>(
    var oldValue: S?,
    var newValue: S?,
    val incoming: Boolean,
    val provider: SessionProvider<S>
)

internal val SessionDataKey = AttributeKey<StatefulSession>("SessionKey")

private fun ApplicationCall.reportMissingSession(): Nothing {
    application.plugin(Sessions) // ensure the plugin is installed
    throw SessionNotYetConfiguredException()
}

/**
 * Thrown when an HTTP response has already been sent but an attempt to modify the session is made.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.TooLateSessionSetException)
 */
public class TooLateSessionSetException :
    IllegalStateException("It's too late to set session: response most likely already has been sent")

/**
 * Thrown when a session is asked too early before the [Sessions] plugin had chance to configure it.
 * For example, in a phase before [ApplicationCallPipeline.Plugins] or in a plugin installed before [Sessions] into
 * the same phase.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SessionNotYetConfiguredException)
 */
public class SessionNotYetConfiguredException :
    IllegalStateException("Sessions are not yet ready: you are asking it to early before the Sessions plugin.")

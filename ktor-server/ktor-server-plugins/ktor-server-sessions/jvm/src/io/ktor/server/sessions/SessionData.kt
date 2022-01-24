/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.server.application.*
import io.ktor.util.*
import kotlin.reflect.*

/**
 * Get current session or fail if no session plugin installed
 * @throws MissingApplicationPluginException
 */
public val ApplicationCall.sessions: CurrentSession
    get() = attributes.getOrNull(SessionDataKey) ?: reportMissingSession()

/**
 * Represents a container for all session instances
 */
public interface CurrentSession {
    /**
     * Set new session instance with [name]
     * @throws IllegalStateException if no session provider registered with for [name]
     */
    public fun set(name: String, value: Any?)

    /**
     * Get session instance for [name]
     * @throws IllegalStateException if no session provider registered with for [name]
     */
    public fun get(name: String): Any?

    /**
     * Clear session instance for [name]
     * @throws IllegalStateException if no session provider registered with for [name]
     */
    public fun clear(name: String)

    /**
     * Find session name for the specified [type] or fail if not found
     * @throws IllegalStateException if no session provider registered for [type]
     */
    public fun findName(type: KClass<*>): String
}

/**
 * Set session instance with type [T]
 * @throws IllegalStateException if no session provider registered for type [T]
 */
public inline fun <reified T : Any> CurrentSession.set(value: T?): Unit = set(findName(T::class), value)

/**
 * Get session instance with type [T]
 * @throws IllegalStateException if no session provider registered for type [T]
 */
public inline fun <reified T : Any> CurrentSession.get(): T? = get(T::class)

/**
 * Get session instance with type [T]
 * @throws IllegalStateException if no session provider registered for type [T]
 */
@Suppress("UNCHECKED_CAST")
public fun <T : Any> CurrentSession.get(klass: KClass<T>): T? = get(findName(klass)) as T?

/**
 * Clear session instance with type [T]
 * @throws IllegalStateException if no session provider registered for type [T]
 */
public inline fun <reified T : Any> CurrentSession.clear(): Unit = clear(findName(T::class))

/**
 * Get or generate a new session instance using [generator] with type [T] (or [name] if specified)
 * @throws IllegalStateException if no session provider registered for type [T] (or [name] if specified)
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
) : CurrentSession {

    private var committed = false

    internal fun commit() {
        committed = true
    }

    override fun findName(type: KClass<*>): String {
        val entry = providerData.entries.firstOrNull { it.value.provider.type == type }
            ?: throw IllegalArgumentException("Session data for type `$type` was not registered")
        return entry.value.provider.name
    }

    @OptIn(InternalAPI::class)
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
        data.value = value as S
    }

    override fun get(name: String): Any? {
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        return providerData.value
    }

    override fun clear(name: String) {
        val providerData =
            providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        providerData.value = null
    }
}

internal suspend fun <S : Any> SessionProvider<S>.receiveSessionData(call: ApplicationCall): SessionProviderData<S> {
    val receivedValue = transport.receive(call)
    val unwrapped = tracker.load(call, receivedValue)
    val incoming = receivedValue != null || unwrapped != null
    return SessionProviderData(unwrapped, incoming, this)
}

internal suspend fun <S : Any> SessionProviderData<S>.sendSessionData(call: ApplicationCall) {
    val value = value
    when {
        value != null -> {
            val wrapped = provider.tracker.store(call, value)
            provider.transport.send(call, wrapped)
        }
        incoming -> {
            /* Deleted session should be cleared off */
            provider.transport.clear(call)
            provider.tracker.clear(call)
        }
    }
}

internal data class SessionProviderData<S : Any>(var value: S?, val incoming: Boolean, val provider: SessionProvider<S>)

internal val SessionDataKey = AttributeKey<SessionData>("SessionKey")

@OptIn(InternalAPI::class)
private fun ApplicationCall.reportMissingSession(): Nothing {
    application.plugin(Sessions) // ensure the plugin is installed
    throw SessionNotYetConfiguredException()
}

/**
 * This exception is thrown when HTTP response has already been sent but an attempt to modify session is made
 */
public class TooLateSessionSetException :
    IllegalStateException("It's too late to set session: response most likely already has been sent")

/**
 * This exception is thrown when a session is asked too early before the [Sessions] plugin had chance to configure it.
 * For example, in a phase before [ApplicationCallPipeline.Plugins] or in a plugin installed before [Sessions] into
 * the same phase.
 */
public class SessionNotYetConfiguredException :
    IllegalStateException("Sessions are not yet ready: you are asking it to early before the Sessions plugin.")

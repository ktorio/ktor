/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.sessions

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.util.*
import kotlin.reflect.*

/**
 * Sessions feature that provides a mechanism to persist information between requests.
 * @property providers list of session providers
 */
public class Sessions(public val providers: List<SessionProvider<*>>) {
    /**
     * Sessions configuration builder
     */
    public class Configuration {
        private val registered = ArrayList<SessionProvider<*>>()

        /**
         * List of session providers to be registered
         */
        public val providers: List<SessionProvider<*>> get() = registered.toList()

        /**
         * Register a session [provider]
         */
        public fun register(provider: SessionProvider<*>) {
            registered.firstOrNull { it.name == provider.name }?.let { alreadyRegistered ->
                throw IllegalArgumentException(
                    "There is already registered session provider with " +
                        "name ${provider.name}: $alreadyRegistered"
                )
            }

            registered.firstOrNull { it.type == provider.type }?.let { alreadyRegistered ->
                throw IllegalArgumentException(
                    "There is already registered session provider for type" +
                        " ${provider.type}: $alreadyRegistered"
                )
            }

            registered.add(provider)
        }
    }

    /**
     * Feature installation object
     */
    public companion object Feature : ApplicationFeature<ApplicationCallPipeline, Sessions.Configuration, Sessions> {
        override val key: AttributeKey<Sessions> = AttributeKey<Sessions>("Sessions")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Sessions {
            val configuration = Configuration().apply(configure)
            val sessions = Sessions(configuration.providers)

            // For each call, call each provider and retrieve session data if needed.
            // Capture data in the attribute's value
            pipeline.intercept(ApplicationCallPipeline.Features) {
                val providerData = sessions.providers.associateBy({ it.name }) {
                    it.receiveSessionData(call)
                }
                val sessionData = SessionData(sessions, providerData)
                call.attributes.put(SessionKey, sessionData)
            }

            // When response is being sent, call each provider to update/remove session data
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Before) {
                val sessionData = call.attributes.getOrNull(SessionKey)
                if (sessionData == null) {
                    // If sessionData is not available it means response happened before Session feature got a
                    // chance to deserialize the data. We should ignore this call in this case.
                    // An example would be CORS feature responding with 403 Forbidden
                    return@intercept
                }

                sessionData.providerData.values.forEach { data ->
                    data.sendSessionData(call)
                }

                sessionData.commit()
            }

            return sessions
        }
    }
}

/**
 * Get current session or fail if no session feature installed
 * @throws MissingApplicationFeatureException
 */
public val ApplicationCall.sessions: CurrentSession
    get() = attributes.getOrNull(SessionKey) ?: reportMissingSession()

private fun ApplicationCall.reportMissingSession(): Nothing {
    application.feature(Sessions) // ensure the feature is installed
    throw SessionNotYetConfiguredException()
}

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
public inline fun <reified T> CurrentSession.set(value: T?): Unit = set(findName(T::class), value)

/**
 * Get session instance with type [T]
 * @throws IllegalStateException if no session provider registered for type [T]
 */
public inline fun <reified T> CurrentSession.get(): T? = get(findName(T::class)) as T?

/**
 * Clear session instance with type [T]
 * @throws IllegalStateException if no session provider registered for type [T]
 */
public inline fun <reified T> CurrentSession.clear(): Unit = clear(findName(T::class))

/**
 * Get or generate a new session instance using [generator] with type [T] (or [name] if specified)
 * @throws IllegalStateException if no session provider registered for type [T] (or [name] if specified)
 */
public inline fun <reified T> CurrentSession.getOrSet(name: String = findName(T::class), generator: () -> T): T {
    val result = get<T>()

    if (result != null) {
        return result
    }

    return generator().apply {
        set(name, this)
    }
}

private data class SessionData(
    val sessions: Sessions,
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

private suspend fun <S : Any> SessionProvider<S>.receiveSessionData(call: ApplicationCall): SessionProviderData<S> {
    val receivedValue = transport.receive(call)
    val unwrapped = tracker.load(call, receivedValue)
    val incoming = receivedValue != null || unwrapped != null
    return SessionProviderData(unwrapped, incoming, this)
}

private suspend fun <S : Any> SessionProviderData<S>.sendSessionData(call: ApplicationCall) {
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

private data class SessionProviderData<S : Any>(var value: S?, val incoming: Boolean, val provider: SessionProvider<S>)

private val SessionKey = AttributeKey<SessionData>("SessionKey")

/**
 * This exception is thrown when HTTP response has already been sent but an attempt to modify session is made
 */
@InternalAPI
public class TooLateSessionSetException :
    IllegalStateException("It's too late to set session: response most likely already has been sent")

/**
 * This exception is thrown when a session is asked too early before the [Sessions] feature had chance to configure it.
 * For example, in a phase before [ApplicationCallPipeline.Features] or in a feature installed before [Sessions] into
 * the same phase.
 */
@InternalAPI
public class SessionNotYetConfiguredException :
    IllegalStateException("Sessions are not yet ready: you are asking it to early before the Sessions feature.")

package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import kotlin.reflect.*

class Sessions(val providers: List<SessionProvider>) {
    class Configuration {
        val providers = mutableListOf<SessionProvider>()

        fun register(provider: SessionProvider) {
            // todo: check that type & name is unique
            providers.add(provider)
        }
    }

    companion object : ApplicationFeature<ApplicationCallPipeline, Sessions.Configuration, Sessions> {
        override val key = AttributeKey<Sessions>("Sessions")
        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): Sessions {
            val configuration = Sessions.Configuration().apply(configure)
            val sessions = Sessions(configuration.providers)

            // For each call, call each provider and retrieve session data if needed.
            // Capture data in the attribute's value
            pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
                val providerData = sessions.providers.associateBy({ it.name }) {
                    val receivedValue = it.transport.receive(call)
                    val unwrapped = it.tracker.load(call, receivedValue)
                    val state = if (unwrapped != null) SessionValueState.Provided else SessionValueState.None
                    SessionProviderData(unwrapped, state, it)
                }
                val sessionData = SessionData(sessions, providerData)
                call.attributes.put(SessionKey, sessionData)
            }

            // When response is being sent, call each provider to update/remove session data
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.Before) {
                val sessionData = call.attributes.getOrNull(SessionKey) ?: throw IllegalStateException("Sessions feature is installed inconsistently")
                sessionData.providerData.forEach { (_, data) ->
                    when (data.state) {
                        SessionValueState.None -> {
                            val value = data.value
                            if (value != null)
                                throw IllegalStateException("Session data shouldn be null in None state")
                            /* if value is None, there were neither incoming nor outgoing session */
                        }
                        SessionValueState.Provided -> {
                            /* Incoming or new or modified session should be sent back */
                            val value = data.value
                            value ?: throw IllegalStateException("Session data shouldn't be null in Modified state")
                            val wrapped = data.provider.tracker.store(call, value)
                            data.provider.transport.send(call, wrapped)
                        }
                        SessionValueState.Deleted -> {
                            /* Deleted session should be cleared off */
                            val value = data.value
                            if (value != null)
                                throw IllegalStateException("Session data shouldn be null in Deleted state")
                            data.provider.transport.clear(call)
                            data.provider.tracker.clear(call)
                        }
                    }
                }
            }

            return sessions
        }
    }
}

val ApplicationCall.sessions: CurrentSession
    get() = attributes.getOrNull(SessionKey) ?: throw IllegalStateException("Sessions feature should be installed to use sessions")

interface CurrentSession {
    fun set(name: String, value: Any?)
    fun get(name: String): Any?
    fun clear(name: String)
    fun findName(type: KClass<*>): String
}

inline fun <reified T> CurrentSession.set(value: T?) = set(findName(T::class), value)
inline fun <reified T> CurrentSession.get(): T? = get(findName(T::class)) as T?
inline fun <reified T> CurrentSession.clear() = clear(findName(T::class))

private data class SessionData(val sessions: Sessions,
                               val providerData: Map<String, SessionProviderData>) : CurrentSession {

    override fun findName(type: KClass<*>): String {
        val entry = providerData.entries.firstOrNull { it.value.provider.type == type } ?:
                throw IllegalArgumentException("Session data for type `$type` was not registered")
        return entry.value.provider.name
    }

    override fun set(name: String, value: Any?) {
        val providerData = providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        val state = when {
            value != null -> {
                providerData.provider.tracker.validate(value)
                SessionValueState.Provided
            }
            providerData.state == SessionValueState.None -> SessionValueState.None
            else -> SessionValueState.Deleted
        }
        providerData.value = value
        providerData.state = state
    }

    override fun get(name: String): Any? {
        val providerData = providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        return providerData.value
    }

    override fun clear(name: String) {
        val providerData = providerData[name] ?: throw IllegalStateException("Session data for `$name` was not registered")
        val state = if (providerData.state == SessionValueState.None) SessionValueState.None else SessionValueState.Deleted
        providerData.value = null
        providerData.state = state
    }
}

private data class SessionProviderData(var value: Any?, var state: SessionValueState, val provider: SessionProvider)

private enum class SessionValueState { None, Provided, Deleted }

private val SessionKey = AttributeKey<SessionData>("SessionKey")


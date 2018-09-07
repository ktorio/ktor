package io.ktor.application

import io.ktor.util.internal.*
import kotlinx.coroutines.*
import java.util.concurrent.*

/**
 * Provides events for [Application] lifecycle
 */
class ApplicationEvents {
    private val handlers = ConcurrentHashMap<EventDefinition<*>, LockFreeLinkedListHead>()

    /**
     * Subscribe [handler] to an event specified by [definition]
     */
    fun <T> subscribe(definition: EventDefinition<T>, handler: EventHandler<T>): DisposableHandle {
        val registration = HandlerRegistration(handler)
        handlers.computeIfAbsent(definition) { LockFreeLinkedListHead() }.addLast(registration)
        return registration
    }

    /**
     * Unsubscribe [handler] from an event specified by [definition]
     */
    fun <T> unsubscribe(definition: EventDefinition<T>, handler: EventHandler<T>) {
        handlers[definition]?.forEach<HandlerRegistration> {
            if (it.handler == handler) it.remove()
        }
    }

    /**
     * Rise an event specified by [definition] with the specified [value] and call all handlers
     *
     * Handlers are called in order of subscriptions.
     * If some handler throws an exception, all handlers will still run and then exception will be rethrown.
     */
    fun <T> raise(definition: EventDefinition<T>, value: T) {
        var exception: Throwable? = null
        handlers[definition]?.forEach<HandlerRegistration> { registration ->
            try {
                @Suppress("UNCHECKED_CAST")
                (registration.handler as EventHandler<T>)(value)
            } catch (e: Throwable) {
                exception?.addSuppressed(e) ?: run { exception = e }
            }
        }
        exception?.let { throw it }
    }

    private class HandlerRegistration(val handler: EventHandler<*>) : LockFreeLinkedListNode(), DisposableHandle {
        override fun dispose() {
            remove()
        }
    }
}

/**
 * Specifies signature for the event handler
 */
typealias EventHandler<T> = (T) -> Unit

// TODO: make two things: definition that is kept private to subsystem, and declaration which is public.
// Invoke only by definition, subscribe by declaration

/**
 * Definition of an event
 *
 * @param T specifies what is a type of a value passed to the event
 */
class EventDefinition<T>

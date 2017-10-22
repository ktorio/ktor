package io.ktor.application

import java.util.concurrent.*

/**
 * Provides events for [Application] lifecycle
 */
class ApplicationEvents {
    private val handlers = ConcurrentHashMap<EventDefinition<*>, MutableList<EventHandler<*>>>()

    /**
     * Subscribe [handler] to an event specified by [definition]
     */
    fun <T> subscribe(definition: EventDefinition<T>, handler: EventHandler<T>) {
        handlers.computeIfAbsent(definition) { CopyOnWriteArrayList() }.add(handler)
    }

    /**
     * Unsubscribe [handler] from an event specified by [definition]
     */
    fun <T> unsubscribe(definition: EventDefinition<T>, handler: EventHandler<T>) {
        handlers.computeIfAbsent(definition) { CopyOnWriteArrayList() }.remove(handler)
    }

    /**
     * Rise an event specified by [definition] with the specified [value] and call all handlers
     *
     * Handlers are called in order of subscriptions.
     * If some handler throws an exception, all handlers will still run and then exception will be rethrown.
     */
    fun <T> raise(definition: EventDefinition<T>, value: T) {
        var exception: Throwable? = null
        handlers[definition]?.forEach { handler ->
            try {
                @Suppress("UNCHECKED_CAST")
                (handler as EventHandler<T>)(value)
            } catch (e: Throwable) {
                exception?.addSuppressed(e) ?: run { exception = e }
            }
        }
        exception?.let { throw it }
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

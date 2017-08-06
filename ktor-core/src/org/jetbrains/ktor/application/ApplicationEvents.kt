package org.jetbrains.ktor.application

import java.util.concurrent.*

/**
 * Provides events for [Application] lifecycle
 */
class ApplicationEvents {
    private val handlers = ConcurrentHashMap<EventDefinition<*>, MutableList<EventHandler<*>>>()

    fun <T> subscribe(definition: EventDefinition<T>, handler: EventHandler<T>) {
        handlers.computeIfAbsent(definition) { CopyOnWriteArrayList() }.add(handler)
    }

    fun <T> unsubscribe(definition: EventDefinition<T>, handler: EventHandler<T>) {
        handlers.computeIfAbsent(definition) { CopyOnWriteArrayList() }.remove(handler)
    }

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

typealias EventHandler<T> = (T) -> Unit

// TODO: make two things: definition that is kept private to subsystem, and declaration which is public.
// Invoke only by definition, subscribe by declaration

class EventDefinition<T>

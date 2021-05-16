/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.application

import io.ktor.util.*
import io.ktor.util.internal.*
import kotlinx.coroutines.*

/**
 * Provides events for [Application] lifecycle
 */
public class ApplicationEvents {
    private val handlers = CopyOnWriteHashMap<EventDefinition<*>, LockFreeLinkedListHead>()

    /**
     * Subscribe [handler] to an event specified by [definition]
     */
    public fun <T> subscribe(definition: EventDefinition<T>, handler: EventHandler<T>): DisposableHandle {
        val registration = HandlerRegistration(handler)
        handlers.computeIfAbsent(definition) { LockFreeLinkedListHead() }.addLast(registration)
        return registration
    }

    /**
     * Unsubscribe [handler] from an event specified by [definition]
     */
    public fun <T> unsubscribe(definition: EventDefinition<T>, handler: EventHandler<T>) {
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
    public fun <T> raise(definition: EventDefinition<T>, value: T) {
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
public typealias EventHandler<T> = (T) -> Unit

// TODO: make two things: definition that is kept private to subsystem, and declaration which is public.
// Invoke only by definition, subscribe by declaration

/**
 * Definition of an event.
 * Event is used as a key so both [hashCode] and [equals] need to be implemented properly.
 * Inheriting of this class is an experimental feature.
 * Instantiate directly if inheritance not necessary.
 *
 * @param T specifies what is a type of a value passed to the event
 */
public open class EventDefinition<T>

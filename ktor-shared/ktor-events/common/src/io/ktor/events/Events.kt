/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.events

import io.ktor.util.collections.*
import io.ktor.util.internal.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

@OptIn(InternalAPI::class)
public class Events {
    private val handlers = CopyOnWriteHashMap<EventDefinition<*>, LockFreeLinkedListHead>()

    /**
     * Subscribe [handler] to an event specified by [definition]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.events.Events.subscribe)
     */
    public fun <T> subscribe(definition: EventDefinition<T>, handler: EventHandler<T>): DisposableHandle {
        val registration = HandlerRegistration(handler)
        handlers.computeIfAbsent(definition) { LockFreeLinkedListHead() }.addLast(registration)
        return registration
    }

    /**
     * Unsubscribe [handler] from an event specified by [definition]
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.events.Events.unsubscribe)
     */
    public fun <T> unsubscribe(definition: EventDefinition<T>, handler: EventHandler<T>) {
        handlers[definition]?.forEach<HandlerRegistration> {
            if (it.handler == handler) it.remove()
        }
    }

    /**
     * Raises the event specified by [definition] with the [value] and calls all handlers.
     *
     * Handlers are called in order of subscriptions.
     * If some handler throws an exception, all remaining handlers will still run. The exception will eventually be re-thrown.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.events.Events.raise)
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
 * Raises an event the same way as [Events.raise] but catches an exception and logs it if the [logger] is provided
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.events.raiseCatching)
 */
public fun <T> Events.raiseCatching(definition: EventDefinition<T>, value: T, logger: Logger? = null) {
    try {
        raise(definition, value)
    } catch (cause: Throwable) {
        logger?.error("Some handlers have thrown an exception", cause)
    }
}

/**
 * Specifies signature for the event handler
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.events.EventHandler)
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.events.EventDefinition)
 *
 * @param T specifies what is a type of value passed to the event
 */
public open class EventDefinition<T>

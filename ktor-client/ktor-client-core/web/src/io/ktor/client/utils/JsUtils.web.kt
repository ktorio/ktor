/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.utils

import kotlinx.coroutines.DisposableHandle
import org.w3c.dom.AddEventListenerOptions
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import kotlin.js.JsAny
import kotlin.js.unsafeCast

/**
 * Registers an event listener for [event] and returns a [DisposableHandle] to remove it.
 *
 * The event is unsafe-cast to [T] before invoking [listener]. The cast is unchecked because JS
 * event objects from some runtimes (e.g. the `ws` npm package) are plain objects that do not
 * satisfy Kotlin `instanceof` checks for DOM event subclasses.
 *
 * @param event event name to listen for
 * @param once if true, the listener removes itself after the first event
 * @param listener callback invoked when the event fires
 * @return a handle to remove the listener
 */
internal inline fun <reified T : Event> EventTarget.addEventListener(
    event: String,
    once: Boolean = false,
    crossinline listener: (T) -> Unit
): DisposableHandle {
    val callback = eventCallback<T>(listener)
    val options = AddEventListenerOptions(once = once)
    addEventListener(event, callback, options)
    return DisposableHandle { removeEventListener(event, callback) }
}

/**
 * Registers event listeners for multiple events and returns a [DisposableHandle] to remove them all.
 * The listener fires once for whichever event occurs first, then optionally removes itself.
 *
 * The event is unsafe-cast to [T] before invoking [listener]. The cast is unchecked because JS
 * event objects from some runtimes (e.g. the `ws` npm package) are plain objects that do not
 * satisfy Kotlin `instanceof` checks for DOM event subclasses.
 *
 * @param event first event name to listen for
 * @param events additional event names to listen for
 * @param once if true, all listeners remove themselves after the first event fires on any of them
 * @param listener callback invoked when any of the events fire
 * @return a handle to remove all listeners
 */
internal inline fun <reified T : Event> EventTarget.addEventListener(
    event: String,
    vararg events: String,
    once: Boolean = false,
    crossinline listener: (T) -> Unit
): DisposableHandle {
    val events = listOf(event, *events)
    lateinit var callback: (JsAny) -> Unit
    val disposable = DisposableHandle { events.forEach { removeEventListener(it, callback) } }
    callback = eventCallback<T> {
        if (once) disposable.dispose()
        listener(it)
    }
    events.forEach { addEventListener(it, callback) }
    return disposable
}

// The callback parameter should be JsAny, not Event, by design.
// Kotlin/WasmJS generates a JS adapter for each lambda based on its declared parameter type:
// a `(Event) -> Unit` adapter checks `x instanceof globalThis.Event` before invoking the lambda.
// The `ws` npm package defines its own `Event` hierarchy that does not extend `globalThis.Event`
// (see https://github.com/websockets/ws/issues/1818), so that check fails for every event fired
// by a Node.js WebSocket.
// Using `(JsAny) -> Unit` produces an adapter with no instanceof check, accepting any JS value.
// This is valid Kotlin: `(JsAny) -> Unit` is a subtype of `(Event) -> Unit` by contravariance.
// `unsafeCast<T>()` inside the body is a no-op — it changes only the compile-time type with no
// runtime check — so it never fails regardless of the actual JS object type.
private inline fun <reified T : Event> eventCallback(crossinline handler: (T) -> Unit): (JsAny) -> Unit {
    @Suppress("RemoveExplicitTypeArguments") // Compiler fails to infer type arguments otherwise
    return { event: JsAny -> handler(event.unsafeCast<T>()) }
}

internal expect fun Event.asString(): String

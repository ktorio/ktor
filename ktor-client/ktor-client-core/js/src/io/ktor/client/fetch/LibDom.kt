/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.fetch

import kotlin.js.Promise

// external fun fetch(input: Request, init: RequestInit? = definedExternally): Promise<Response>

public external fun fetch(input: String, init: RequestInit? = definedExternally): Promise<Response>

public external interface Request : Body {
    /* "default" | "no-store" | "reload" | "no-cache" | "force-cache" | "only-if-cached" */
    public var cache: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "omit" | "same-origin" | "include" */
    public var credentials: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "" | "audio" | "audioworklet" | "document" | "embed" | "font" | "image" | "manifest" | "object" | "paintworklet" | "report" | "script" | "sharedworker" | "style" | "track" | "video" | "worker" | "xslt" */
    public var destination: dynamic
        get() = definedExternally
        set(value) = definedExternally
    public var headers: Headers
    public var integrity: String
    public var isHistoryNavigation: Boolean
    public var isReloadNavigation: Boolean
    public var keepalive: Boolean
    public var method: String

    /* "navigate" | "same-origin" | "no-cors" | "cors" */
    public var mode: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "follow" | "error" | "manual" */
    public var redirect: dynamic
        get() = definedExternally
        set(value) = definedExternally
    public var referrer: String

    /* "" | "no-referrer" | "no-referrer-when-downgrade" | "same-origin" | "origin" | "strict-origin" | "origin-when-cross-origin" | "strict-origin-when-cross-origin" | "unsafe-url" */
    public var referrerPolicy: dynamic
        get() = definedExternally
        set(value) = definedExternally

    public var signal: AbortSignal

    public var url: String

    public fun clone(): Request
}

public external interface Response : Body {
    public var headers: Headers
    public var ok: Boolean
    public var redirected: Boolean
    public var status: Number
    public var statusText: String
    public var trailer: Promise<Headers>

    /* "basic" | "cors" | "default" | "error" | "opaque" | "opaqueredirect" */
    public var type: dynamic
        get() = definedExternally
        set(value) = definedExternally

    public var url: String
    public fun clone(): Response
}

public external interface Body {
    public var body: ReadableStream<Uint8Array>?
        get() = definedExternally
        set(value) = definedExternally
    public var bodyUsed: Boolean
    public fun arrayBuffer(): Promise<ArrayBuffer>
    public fun blob(): Promise<Blob>
    public fun formData(): Promise<FormData>
    public fun json(): Promise<Any>
    public fun text(): Promise<String>
}

public external interface FormData {
    public fun append(name: String, value: String, fileName: String? = definedExternally)
    public fun append(name: String, value: Blob, fileName: String? = definedExternally)
    public fun delete(name: String)

    /* File | String */
    public fun get(name: String): dynamic
    public fun getAll(name: String): Array<dynamic /* File | String */>
    public fun has(name: String): Boolean
    public fun set(name: String, value: String, fileName: String? = definedExternally)
    public fun set(name: String, value: Blob, fileName: String? = definedExternally)
    public fun forEach(
        callbackfn: (value: dynamic /* File | String */, key: String, parent: FormData) -> Unit,
        thisArg: Any? = definedExternally
    )
}

public external interface Blob {
    public var size: Number
    public var type: String
    public fun slice(
        start: Number? = definedExternally,
        end: Number? = definedExternally,
        contentType: String? = definedExternally
    ): Blob
}

public external interface ReadableStream<R> {
    public var locked: Boolean
    public fun cancel(reason: Any? = definedExternally): Promise<Unit>
    public fun getReader(options: dynamic): ReadableStreamBYOBReader
    public fun getReader(): ReadableStreamDefaultReader<R>

//    fun <T> pipeThrough(__0: `T$1`, options: PipeOptions? = definedExternally): ReadableStream<T>

    public fun pipeTo(dest: WritableStream<R>, options: PipeOptions? = definedExternally): Promise<Unit>

    /* JsTuple<ReadableStream<R>, ReadableStream<R>> */
    public fun tee(): dynamic
}

public external interface PipeOptions {
    public var preventAbort: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var preventCancel: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var preventClose: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var signal: AbortSignal?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface WritableStream<W> {
    public var locked: Boolean
    public fun abort(reason: Any? = definedExternally): Promise<Unit>
    public fun getWriter(): WritableStreamDefaultWriter<W>
}

public external interface WritableStreamDefaultWriter<W> {
    public var closed: Promise<Unit>
    public var desiredSize: Number?
        get() = definedExternally
        set(value) = definedExternally
    public var ready: Promise<Unit>
    public fun abort(reason: Any? = definedExternally): Promise<Unit>
    public fun close(): Promise<Unit>
    public fun releaseLock()
    public fun write(chunk: W): Promise<Unit>
}

public external interface ReadableStreamBYOBReader {
    public var closed: Promise<Unit>
    public fun cancel(reason: Any? = definedExternally): Promise<Unit>
    public fun <T : ArrayBufferView> read(view: T): Promise<ReadableStreamReadResult<T>>
    public fun releaseLock()
}

public external interface ReadableStreamDefaultReader<R> {
    public var closed: Promise<Unit>
    public fun cancel(reason: Any? = definedExternally): Promise<Unit>
    public fun read(): Promise<ReadableStreamReadResult<R>>
    public fun releaseLock()
}

public external interface ReadableStreamReadResult<T> {
    public var done: Boolean
    public var value: T
}

public external interface Headers {
    public fun append(name: String, value: String)
    public fun delete(name: String)
    public fun get(name: String): String?
    public fun has(name: String): Boolean
    public fun set(name: String, value: String)
    public fun forEach(
        callbackfn: (value: String, key: String, parent: Headers) -> Unit,
        thisArg: Any? = definedExternally
    )
}

public external interface RequestInit {
    /* Blob | ArrayBufferView | ArrayBuffer | FormData | URLSearchParams | ReadableStream<Uint8Array> | String */
    public var body: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "default" | "no-store" | "reload" | "no-cache" | "force-cache" | "only-if-cached" */
    public var cache: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "omit" | "same-origin" | "include" */
    public var credentials: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* Headers | Array<Array<String>> | Record<String, String> */
    public var headers: dynamic
        get() = definedExternally
        set(value) = definedExternally

    public var integrity: String?
        get() = definedExternally
        set(value) = definedExternally

    public var keepalive: Boolean?
        get() = definedExternally
        set(value) = definedExternally

    public var method: String?
        get() = definedExternally
        set(value) = definedExternally

    /* "navigate" | "same-origin" | "no-cors" | "cors" */
    public var mode: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "follow" | "error" | "manual" */
    public var redirect: dynamic
        get() = definedExternally
        set(value) = definedExternally
    public var referrer: String?
        get() = definedExternally
        set(value) = definedExternally

    /* "" | "no-referrer" | "no-referrer-when-downgrade" | "same-origin" | "origin" | "strict-origin" | "origin-when-cross-origin" | "strict-origin-when-cross-origin" | "unsafe-url" */
    public var referrerPolicy: dynamic
        get() = definedExternally
        set(value) = definedExternally

    public var signal: AbortSignal?
        get() = definedExternally
        set(value) = definedExternally

    public var window: Any?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface AbortController {
    public var signal: AbortSignal
    public fun abort()
}

public external interface AbortSignal : EventTarget {
    public var aborted: Boolean
    public var onabort: ((AbortSignal, ev: Event) -> Any)?
        get() = definedExternally
        set(value) = definedExternally

    public fun <K : Any> addEventListener(
        type: K,
        listener: (AbortSignal, ev: Any) -> Any,
        options: Boolean? = definedExternally
    )

    public fun <K : Any> addEventListener(
        type: K,
        listener: (AbortSignal, ev: Any) -> Any,
        options: AddEventListenerOptions? = definedExternally
    )

    override fun addEventListener(type: String, listener: EventListener, options: Boolean?)
    override fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions?)
    override fun addEventListener(type: String, listener: EventListenerObject, options: Boolean?)
    override fun addEventListener(type: String, listener: EventListenerObject, options: AddEventListenerOptions?)
    public fun <K : Any> removeEventListener(
        type: K,
        listener: (AbortSignal, ev: Any) -> Any,
        options: Boolean? = definedExternally
    )

    public fun <K : Any> removeEventListener(
        type: K,
        listener: (AbortSignal, ev: Any) -> Any,
        options: EventListenerOptions? = definedExternally
    )

    override fun removeEventListener(type: String, listener: EventListener, options: Boolean?)
    override fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions?)
    override fun removeEventListener(type: String, listener: EventListenerObject, options: Boolean?)
    override fun removeEventListener(type: String, listener: EventListenerObject, options: EventListenerOptions?)
    public fun <K : Any> addEventListener(type: K, listener: (AbortSignal, ev: Any) -> Any)
    override fun addEventListener(type: String, listener: EventListener)
    override fun addEventListener(type: String, listener: EventListenerObject)
    public fun <K : Any> removeEventListener(type: K, listener: (AbortSignal, ev: Any) -> Any)
    override fun removeEventListener(type: String, listener: EventListener)
    override fun removeEventListener(type: String, listener: EventListenerObject)
}

public external interface EventTarget {
    public fun addEventListener(type: String, listener: EventListener, options: Boolean? = definedExternally)
    public fun addEventListener(
        type: String,
        listener: EventListener,
        options: AddEventListenerOptions? = definedExternally
    )

    public fun addEventListener(type: String, listener: EventListenerObject, options: Boolean? = definedExternally)
    public fun addEventListener(
        type: String,
        listener: EventListenerObject,
        options: AddEventListenerOptions? = definedExternally
    )

    public fun dispatchEvent(event: Event): Boolean
    public fun removeEventListener(
        type: String,
        callback: EventListener,
        options: EventListenerOptions? = definedExternally
    )

    public fun removeEventListener(type: String, callback: EventListener, options: Boolean? = definedExternally)
    public fun removeEventListener(
        type: String,
        callback: EventListenerObject,
        options: EventListenerOptions? = definedExternally
    )

    public fun removeEventListener(type: String, callback: EventListenerObject, options: Boolean? = definedExternally)
    public fun addEventListener(type: String, listener: EventListener)
    public fun addEventListener(type: String, listener: EventListenerObject)
    public fun removeEventListener(type: String, callback: EventListener)
    public fun removeEventListener(type: String, callback: EventListenerObject)
}

public external interface EventListener

@Suppress("NOTHING_TO_INLINE")
public inline operator fun EventListener.invoke(evt: Event) {
    asDynamic()(evt)
}

public external interface AddEventListenerOptions : EventListenerOptions {
    public var once: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    public var passive: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface EventListenerOptions {
    public var capture: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

public external interface EventListenerObject {
    public fun handleEvent(evt: Event)
}

public external interface Event {
    public var bubbles: Boolean
    public var cancelBubble: Boolean
    public var cancelable: Boolean
    public var composed: Boolean
    public var currentTarget: EventTarget?
        get() = definedExternally
        set(value) = definedExternally
    public var defaultPrevented: Boolean
    public var eventPhase: Number
    public var isTrusted: Boolean
    public var returnValue: Boolean
    public var srcElement: EventTarget?
        get() = definedExternally
        set(value) = definedExternally
    public var target: EventTarget?
        get() = definedExternally
        set(value) = definedExternally
    public var timeStamp: Number
    public var type: String
    public fun composedPath(): Array<EventTarget>
    public fun initEvent(type: String, bubbles: Boolean? = definedExternally, cancelable: Boolean? = definedExternally)
    public fun preventDefault()
    public fun stopImmediatePropagation()
    public fun stopPropagation()
    public var AT_TARGET: Number
    public var BUBBLING_PHASE: Number
    public var CAPTURING_PHASE: Number
    public var NONE: Number
}

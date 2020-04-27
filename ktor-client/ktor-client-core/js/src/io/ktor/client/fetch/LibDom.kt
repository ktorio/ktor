/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.fetch

import kotlin.js.Promise

//external fun fetch(input: Request, init: RequestInit? = definedExternally): Promise<Response>

external fun fetch(input: String, init: RequestInit? = definedExternally): Promise<Response>

external interface Request : Body {
    /* "default" | "no-store" | "reload" | "no-cache" | "force-cache" | "only-if-cached" */
    var cache: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "omit" | "same-origin" | "include" */
    var credentials: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "" | "audio" | "audioworklet" | "document" | "embed" | "font" | "image" | "manifest" | "object" | "paintworklet" | "report" | "script" | "sharedworker" | "style" | "track" | "video" | "worker" | "xslt" */
    var destination: dynamic
        get() = definedExternally
        set(value) = definedExternally
    var headers: Headers
    var integrity: String
    var isHistoryNavigation: Boolean
    var isReloadNavigation: Boolean
    var keepalive: Boolean
    var method: String

    /* "navigate" | "same-origin" | "no-cors" | "cors" */
    var mode: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "follow" | "error" | "manual" */
    var redirect: dynamic
        get() = definedExternally
        set(value) = definedExternally
    var referrer: String

    /* "" | "no-referrer" | "no-referrer-when-downgrade" | "same-origin" | "origin" | "strict-origin" | "origin-when-cross-origin" | "strict-origin-when-cross-origin" | "unsafe-url" */
    var referrerPolicy: dynamic
        get() = definedExternally
        set(value) = definedExternally

    var signal: AbortSignal

    var url: String

    fun clone(): Request
}

external interface Response : Body {
    var headers: Headers
    var ok: Boolean
    var redirected: Boolean
    var status: Number
    var statusText: String
    var trailer: Promise<Headers>

    /* "basic" | "cors" | "default" | "error" | "opaque" | "opaqueredirect" */
    var type: dynamic
        get() = definedExternally
        set(value) = definedExternally
    var url: String
    fun clone(): Response
}

external interface Body {
    var body: ReadableStream<Uint8Array>?
        get() = definedExternally
        set(value) = definedExternally
    var bodyUsed: Boolean
    fun arrayBuffer(): Promise<ArrayBuffer>
    fun blob(): Promise<Blob>
    fun formData(): Promise<FormData>
    fun json(): Promise<Any>
    fun text(): Promise<String>
}

external interface FormData {
    fun append(name: String, value: String, fileName: String? = definedExternally)
    fun append(name: String, value: Blob, fileName: String? = definedExternally)
    fun delete(name: String)

    /* File | String */
    fun get(name: String): dynamic
    fun getAll(name: String): Array<dynamic /* File | String */>
    fun has(name: String): Boolean
    fun set(name: String, value: String, fileName: String? = definedExternally)
    fun set(name: String, value: Blob, fileName: String? = definedExternally)
    fun forEach(callbackfn: (value: dynamic /* File | String */, key: String, parent: FormData) -> Unit, thisArg: Any? = definedExternally)
}

external interface Blob {
    var size: Number
    var type: String
    fun slice(start: Number? = definedExternally, end: Number? = definedExternally, contentType: String? = definedExternally): Blob
}

external interface ReadableStream<R> {
    var locked: Boolean
    fun cancel(reason: Any? = definedExternally): Promise<Unit>
    fun getReader(options: dynamic): ReadableStreamBYOBReader
    fun getReader(): ReadableStreamDefaultReader<R>

//    fun <T> pipeThrough(__0: `T$1`, options: PipeOptions? = definedExternally): ReadableStream<T>

    fun pipeTo(dest: WritableStream<R>, options: PipeOptions? = definedExternally): Promise<Unit>

    /* JsTuple<ReadableStream<R>, ReadableStream<R>> */
    fun tee(): dynamic
}

external interface PipeOptions {
    var preventAbort: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var preventCancel: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var preventClose: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var signal: AbortSignal?
        get() = definedExternally
        set(value) = definedExternally
}

external interface WritableStream<W> {
    var locked: Boolean
    fun abort(reason: Any? = definedExternally): Promise<Unit>
    fun getWriter(): WritableStreamDefaultWriter<W>
}

external interface WritableStreamDefaultWriter<W> {
    var closed: Promise<Unit>
    var desiredSize: Number?
        get() = definedExternally
        set(value) = definedExternally
    var ready: Promise<Unit>
    fun abort(reason: Any? = definedExternally): Promise<Unit>
    fun close(): Promise<Unit>
    fun releaseLock()
    fun write(chunk: W): Promise<Unit>
}

external interface ReadableStreamBYOBReader {
    var closed: Promise<Unit>
    fun cancel(reason: Any? = definedExternally): Promise<Unit>
    fun <T : ArrayBufferView> read(view: T): Promise<ReadableStreamReadResult<T>>
    fun releaseLock()
}

external interface ReadableStreamDefaultReader<R> {
    var closed: Promise<Unit>
    fun cancel(reason: Any? = definedExternally): Promise<Unit>
    fun read(): Promise<ReadableStreamReadResult<R>>
    fun releaseLock()
}

external interface ReadableStreamReadResult<T> {
    var done: Boolean
    var value: T
}

external interface Headers {
    fun append(name: String, value: String)
    fun delete(name: String)
    fun get(name: String): String?
    fun has(name: String): Boolean
    fun set(name: String, value: String)
    fun forEach(callbackfn: (value: String, key: String, parent: Headers) -> Unit, thisArg: Any? = definedExternally)
}

external interface RequestInit {
    /* Blob | ArrayBufferView | ArrayBuffer | FormData | URLSearchParams | ReadableStream<Uint8Array> | String */
    var body: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "default" | "no-store" | "reload" | "no-cache" | "force-cache" | "only-if-cached" */
    var cache: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "omit" | "same-origin" | "include" */
    var credentials: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* Headers | Array<Array<String>> | Record<String, String> */
    var headers: dynamic
        get() = definedExternally
        set(value) = definedExternally

    var integrity: String?
        get() = definedExternally
        set(value) = definedExternally

    var keepalive: Boolean?
        get() = definedExternally
        set(value) = definedExternally

    var method: String?
        get() = definedExternally
        set(value) = definedExternally

    /* "navigate" | "same-origin" | "no-cors" | "cors" */
    var mode: dynamic
        get() = definedExternally
        set(value) = definedExternally

    /* "follow" | "error" | "manual" */
    var redirect: dynamic
        get() = definedExternally
        set(value) = definedExternally
    var referrer: String?
        get() = definedExternally
        set(value) = definedExternally

    /* "" | "no-referrer" | "no-referrer-when-downgrade" | "same-origin" | "origin" | "strict-origin" | "origin-when-cross-origin" | "strict-origin-when-cross-origin" | "unsafe-url" */
    var referrerPolicy: dynamic
        get() = definedExternally
        set(value) = definedExternally

    var signal: AbortSignal?
        get() = definedExternally
        set(value) = definedExternally

    var window: Any?
        get() = definedExternally
        set(value) = definedExternally
}

external interface AbortController {
    var signal: AbortSignal
    fun abort()
}

external interface AbortSignal : EventTarget {
    var aborted: Boolean
    var onabort: ((AbortSignal, ev: Event) -> Any)?
        get() = definedExternally
        set(value) = definedExternally

    fun <K : Any> addEventListener(
        type: K,
        listener: (AbortSignal, ev: Any) -> Any,
        options: Boolean? = definedExternally
    )

    fun <K : Any> addEventListener(
        type: K,
        listener: (AbortSignal, ev: Any) -> Any,
        options: AddEventListenerOptions? = definedExternally
    )

    override fun addEventListener(type: String, listener: EventListener, options: Boolean?)
    override fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions?)
    override fun addEventListener(type: String, listener: EventListenerObject, options: Boolean?)
    override fun addEventListener(type: String, listener: EventListenerObject, options: AddEventListenerOptions?)
    fun <K : Any> removeEventListener(
        type: K,
        listener: (AbortSignal, ev: Any) -> Any,
        options: Boolean? = definedExternally
    )

    fun <K : Any> removeEventListener(
        type: K,
        listener: (AbortSignal, ev: Any) -> Any,
        options: EventListenerOptions? = definedExternally
    )

    override fun removeEventListener(type: String, listener: EventListener, options: Boolean?)
    override fun removeEventListener(type: String, listener: EventListener, options: EventListenerOptions?)
    override fun removeEventListener(type: String, listener: EventListenerObject, options: Boolean?)
    override fun removeEventListener(type: String, listener: EventListenerObject, options: EventListenerOptions?)
    fun <K : Any> addEventListener(type: K, listener: (AbortSignal, ev: Any) -> Any)
    override fun addEventListener(type: String, listener: EventListener)
    override fun addEventListener(type: String, listener: EventListenerObject)
    fun <K : Any> removeEventListener(type: K, listener: (AbortSignal, ev: Any) -> Any)
    override fun removeEventListener(type: String, listener: EventListener)
    override fun removeEventListener(type: String, listener: EventListenerObject)
}

external interface EventTarget {
    fun addEventListener(type: String, listener: EventListener, options: Boolean? = definedExternally)
    fun addEventListener(type: String, listener: EventListener, options: AddEventListenerOptions? = definedExternally)
    fun addEventListener(type: String, listener: EventListenerObject, options: Boolean? = definedExternally)
    fun addEventListener(
        type: String,
        listener: EventListenerObject,
        options: AddEventListenerOptions? = definedExternally
    )

    fun dispatchEvent(event: Event): Boolean
    fun removeEventListener(type: String, callback: EventListener, options: EventListenerOptions? = definedExternally)
    fun removeEventListener(type: String, callback: EventListener, options: Boolean? = definedExternally)
    fun removeEventListener(
        type: String,
        callback: EventListenerObject,
        options: EventListenerOptions? = definedExternally
    )

    fun removeEventListener(type: String, callback: EventListenerObject, options: Boolean? = definedExternally)
    fun addEventListener(type: String, listener: EventListener)
    fun addEventListener(type: String, listener: EventListenerObject)
    fun removeEventListener(type: String, callback: EventListener)
    fun removeEventListener(type: String, callback: EventListenerObject)
}

external interface EventListener {
    @nativeInvoke
    operator fun invoke(evt: Event)
}

external interface AddEventListenerOptions : EventListenerOptions {
    var once: Boolean?
        get() = definedExternally
        set(value) = definedExternally
    var passive: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface EventListenerOptions {
    var capture: Boolean?
        get() = definedExternally
        set(value) = definedExternally
}

external interface EventListenerObject {
    fun handleEvent(evt: Event)
}

external interface Event {
    var bubbles: Boolean
    var cancelBubble: Boolean
    var cancelable: Boolean
    var composed: Boolean
    var currentTarget: EventTarget?
        get() = definedExternally
        set(value) = definedExternally
    var defaultPrevented: Boolean
    var eventPhase: Number
    var isTrusted: Boolean
    var returnValue: Boolean
    var srcElement: EventTarget?
        get() = definedExternally
        set(value) = definedExternally
    var target: EventTarget?
        get() = definedExternally
        set(value) = definedExternally
    var timeStamp: Number
    var type: String
    fun composedPath(): Array<EventTarget>
    fun initEvent(type: String, bubbles: Boolean? = definedExternally, cancelable: Boolean? = definedExternally)
    fun preventDefault()
    fun stopImmediatePropagation()
    fun stopPropagation()
    var AT_TARGET: Number
    var BUBBLING_PHASE: Number
    var CAPTURING_PHASE: Number
    var NONE: Number
}

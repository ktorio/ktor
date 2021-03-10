/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js.compatibility

import org.w3c.dom.events.*
import org.w3c.fetch.*

// https://youtrack.jetbrains.com/issue/KT-29243
/**
 * https://fetch.spec.whatwg.org/#dom-request-signal
 */
public var RequestInit.signal: AbortSignal
    get() = asDynamic().signal as AbortSignal
    set(newValue) {
        asDynamic().signal = newValue
    }

/**
 * Exposes the JavaScript [AbortController](https://dom.spec.whatwg.org/#interface-abortcontroller) to Kotlin
 */
public open external class AbortController {
    public var signal: AbortSignal
    public fun abort()
}

/**
 * Exposes the JavaScript [AbortSignal](https://dom.spec.whatwg.org/#interface-AbortSignal) to Kotlin
 */
public abstract external class AbortSignal : EventTarget {
    public var aborted: Boolean
    public var onabort: ((AbortSignal, ev: Event) -> Any)?
        get() = definedExternally
        set(value) = definedExternally
}

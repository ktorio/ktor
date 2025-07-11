/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("ConstPropertyName")

package io.ktor.htmx

/**
 * Constants for HTMX request headers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxRequestHeaders)
 *
 * @see [Official documentation](https://htmx.org/events/)
 */
public object HxRequestHeaders {
    /**
     * Indicates that the request is via an element using hx-boost.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxRequestHeaders.Boosted)
     */
    public const val Boosted: String = "HX-Boosted"

    /**
     * The current URL of the browser.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxRequestHeaders.CurrentUrl)
     */
    public const val CurrentUrl: String = "HX-Current-URL"

    /**
     * “True” if the request is for history restoration after a miss in the local history cache.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxRequestHeaders.HistoryRestoreRequest)
     */
    public const val HistoryRestoreRequest: String = "HX-History-Restore-Request"

    /**
     * The user response to an hx-prompt.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxRequestHeaders.Prompt)
     */
    public const val Prompt: String = "HX-Prompt"

    /**
     * Always “true”.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxRequestHeaders.Request)
     */
    public const val Request: String = "HX-Request"

    /**
     * The id of the target element if it exists.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxRequestHeaders.Target)
     */
    public const val Target: String = "HX-Target"

    /**
     * The name of the triggered element if it exists.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxRequestHeaders.TriggerName)
     */
    public const val TriggerName: String = "HX-Trigger-Name"

    /**
     * The id of the triggered element if it exists.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxRequestHeaders.Trigger)
     */
    public const val Trigger: String = "HX-Trigger"
}

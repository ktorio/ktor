/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("ConstPropertyName")

package io.ktor.htmx

/**
 * Constants for HTMX request headers.
 *
 * @see [Official documentation](https://htmx.org/events/)
 */
public object HxRequestHeaders {
    /**
     * Indicates that the request is via an element using hx-boost.
     */
    public const val Boosted: String = "HX-Boosted"

    /**
     * The current URL of the browser.
     */
    public const val CurrentUrl: String = "HX-Current-URL"

    /**
     * “True” if the request is for history restoration after a miss in the local history cache.
     */
    public const val HistoryRestoreRequest: String = "HX-History-Restore-Request"

    /**
     * The user response to an hx-prompt.
     */
    public const val Prompt: String = "HX-Prompt"

    /**
     * Always “true”.
     */
    public const val Request: String = "HX-Request"

    /**
     * The id of the target element if it exists.
     */
    public const val Target: String = "HX-Target"

    /**
     * The name of the triggered element if it exists.
     */
    public const val TriggerName: String = "HX-Trigger-Name"

    /**
     * The id of the triggered element if it exists.
     */
    public const val Trigger: String = "HX-Trigger"
}

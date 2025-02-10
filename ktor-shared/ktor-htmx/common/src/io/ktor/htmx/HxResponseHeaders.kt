/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("ConstPropertyName")

package io.ktor.htmx

/**
 * Constants for HTMX response headers.
 *
 * @see [Official documentation](https://htmx.org/reference/#response_headers)
 */
public object HxResponseHeaders {
    /**
     * Allows you to do a client-side redirect that does not do a full page reload.
     */
    public const val Location: String = "HX-Location"

    /**
     * Pushes a new URL into the history stack.
     */
    public const val PushUrl: String = "HX-Push-Url"

    /**
     * Can be used to do a client-side redirect to a new location.
     */
    public const val Redirect: String = "HX-Redirect"

    /**
     * If set to “true” the client-side will do a full refresh of the page.
     */
    public const val Refresh: String = "HX-Refresh"

    /**
     * Replaces the current URL in the location bar.
     */
    public const val ReplaceUrl: String = "HX-Replace-Url"

    /**
     * Allows you to specify how the response will be swapped. See hx-swap for possible values.
     */
    public const val Reswap: String = "HX-Reswap"

    /**
     * A CSS selector that updates the target of the content update to a different element on the page.
     */
    public const val Retarget: String = "HX-Retarget"

    /**
     * A CSS selector that allows you to choose which part of the response is used to be swapped in. Overrides an existing hx-select on the triggering element.
     */
    public const val Reselect: String = "HX-Reselect"

    /**
     * Allows you to trigger client-side events.
     */
    public const val Trigger: String = "HX-Trigger"

    /**
     * Allows you to trigger client-side events after the settle step.
     */
    public const val TriggerAfterSettle: String = "HX-Trigger-After-Settle"

    /**
     * Allows you to trigger client-side events after the swap step.
     */
    public const val TriggerAfterSwap: String = "HX-Trigger-After-Swap"
}

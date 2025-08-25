/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("ConstPropertyName")

package io.ktor.htmx

/**
 * Constants for HTMX response headers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders)
 *
 * @see [Official documentation](https://htmx.org/reference/#response_headers)
 */
public object HxResponseHeaders {
    /**
     * Allows you to do a client-side redirect that does not do a full page reload.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.Location)
     */
    public const val Location: String = "HX-Location"

    /**
     * Pushes a new URL into the history stack.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.PushUrl)
     */
    public const val PushUrl: String = "HX-Push-Url"

    /**
     * Can be used to do a client-side redirect to a new location.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.Redirect)
     */
    public const val Redirect: String = "HX-Redirect"

    /**
     * If set to “true” the client-side will do a full refresh of the page.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.Refresh)
     */
    public const val Refresh: String = "HX-Refresh"

    /**
     * Replaces the current URL in the location bar.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.ReplaceUrl)
     */
    public const val ReplaceUrl: String = "HX-Replace-Url"

    /**
     * Allows you to specify how the response will be swapped. See hx-swap for possible values.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.Reswap)
     */
    public const val Reswap: String = "HX-Reswap"

    /**
     * A CSS selector that updates the target of the content update to a different element on the page.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.Retarget)
     */
    public const val Retarget: String = "HX-Retarget"

    /**
     * A CSS selector that allows you to choose which part of the response is used to be swapped in. Overrides an existing hx-select on the triggering element.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.Reselect)
     */
    public const val Reselect: String = "HX-Reselect"

    /**
     * Allows you to trigger client-side events.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.Trigger)
     */
    public const val Trigger: String = "HX-Trigger"

    /**
     * Allows you to trigger client-side events after the settle step.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.TriggerAfterSettle)
     */
    public const val TriggerAfterSettle: String = "HX-Trigger-After-Settle"

    /**
     * Allows you to trigger client-side events after the swap step.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxResponseHeaders.TriggerAfterSwap)
     */
    public const val TriggerAfterSwap: String = "HX-Trigger-After-Swap"
}

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("ConstPropertyName")

package io.ktor.htmx

/**
 * Constants for HTMX CSS classes.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxCss)
 *
 * @see [Official documentation](https://htmx.org/reference/#classes)
 */
public object HxCss {
    /**
     * Applied to a new piece of content before it is swapped, removed after it is settled.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxCss.Added)
     */
    public const val Added: String = "htmx-added"

    /**
     * A dynamically generated class that will toggle visible (opacity:1) when a htmx-request class is present
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxCss.Indicator)
     */
    public const val Indicator: String = "htmx-indicator"

    /**
     * Applied to either the element or the element specified with hx-indicator while a request is ongoing
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxCss.Request)
     */
    public const val Request: String = "htmx-request"

    /**
     * Applied to a target after content is swapped, removed after it is settled. The duration can be modified via hx-swap.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxCss.Settling)
     */
    public const val Settling: String = "htmx-settling"

    /**
     * Applied to a target before any content is swapped, removed after it is swapped. The duration can be modified via hx-swap.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxCss.Swapping)
     */
    public const val Swapping: String = "htmx-swapping"
}

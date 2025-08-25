/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ConstPropertyName")

package io.ktor.htmx

/**
 * Constants for "hx-swap" values.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap)
 *
 * @see [Official documentation](https://htmx.org/attributes/hx-swap/)
 */
public object HxSwap {
    /**
     * Replace the inner HTML of the target element
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap.innerHtml)
     */
    public const val innerHtml: String = "innerHtml"

    /**
     * Replace the entire target element with the response
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap.outerHtml)
     */
    public const val outerHtml: String = "outerHTML"

    /**
     * Replace the text content of the target element, without parsing the response as HTML
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap.textContent)
     */
    public const val textContent: String = "textContent"

    /**
     * Insert the response before the target element
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap.beforeBegin)
     */
    public const val beforeBegin: String = "beforebegin"

    /**
     * Insert the response before the first child of the target element
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap.afterBegin)
     */
    public const val afterBegin: String = "afterbegin"

    /**
     * Insert the response after the last child of the target element
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap.beforeEnd)
     */
    public const val beforeEnd: String = "beforeend"

    /**
     * Insert the response after the target element
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap.afterEnd)
     */
    public const val afterEnd: String = "afterend"

    /**
     * Deletes the target element regardless of the response
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap.delete)
     */
    public const val delete: String = "delete"

    /**
     * Does not append content from response (out of band items will still be processed)
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxSwap.none)
     */
    public const val none: String = "none"
}

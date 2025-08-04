/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("ConstPropertyName")

package io.ktor.htmx

/**
 * Attribute constants that are used with HTMX.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys)
 *
 * @see [Official documentation](https://htmx.org/reference/#attributes-additional)
 */
public object HxAttributeKeys {
    /**
     * Issues a GET to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Get)
     */
    public const val Get: String = "hx-get"

    /**
     * Issues a POST to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Post)
     */
    public const val Post: String = "hx-post"

    /**
     * Handles events with inline scripts on elements.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.On)
     */
    public const val On: String = "hx-on"

    /**
     * Pushes a URL into the browser location bar to create history.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.PushUrl)
     */
    public const val PushUrl: String = "hx-push-url"

    /**
     * Selects content to swap in from a response.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Select)
     */
    public const val Select: String = "hx-select"

    /**
     * Selects content to swap in from a response, somewhere other than the target (out of band).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.SelectOob)
     */
    public const val SelectOob: String = "hx-select-oob"

    /**
     * Controls how content will swap in (outerHTML, beforeend, afterend, …).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Swap)
     */
    public const val Swap: String = "hx-swap"

    /**
     * Marks element to swap in from a response (out of band).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.SwapOob)
     */
    public const val SwapOob: String = "hx-swap-oob"

    /**
     * Specifies the target element to be swapped.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Target)
     */
    public const val Target: String = "hx-target"

    /**
     * Specifies the event that triggers the request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Trigger)
     */
    public const val Trigger: String = "hx-trigger"

    /**
     * Adds values to submit with the request (JSON format).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Vals)
     */
    public const val Vals: String = "hx-vals"

    /**
     * Adds progressive enhancement for links and forms.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Boost)
     */
    public const val Boost: String = "hx-boost"

    /**
     * Shows a confirm() dialog before issuing a request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Confirm)
     */
    public const val Confirm: String = "hx-confirm"

    /**
     * Issues a DELETE to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Delete)
     */
    public const val Delete: String = "hx-delete"

    /**
     * Disables htmx processing for the given node and any children nodes.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Disable)
     */
    public const val Disable: String = "hx-disable"

    /**
     * Adds the disabled attribute to the specified elements while a request is in flight.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.DisabledElt)
     */
    public const val DisabledElt: String = "hx-disabled-elt"

    /**
     * Controls and disables automatic attribute inheritance for child nodes.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Disinherit)
     */
    public const val Disinherit: String = "hx-disinherit"

    /**
     * Changes the request encoding type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Encoding)
     */
    public const val Encoding: String = "hx-encoding"

    /**
     * Extensions to use for this element.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Ext)
     */
    public const val Ext: String = "hx-ext"

    /**
     * Adds to the headers that will be submitted with the request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Headers)
     */
    public const val Headers: String = "hx-headers"

    /**
     * Prevents sensitive data from being saved to the history cache.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.History)
     */
    public const val History: String = "hx-history"

    /**
     * Specifies the element to snapshot and restore during history navigation.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.HistoryElt)
     */
    public const val HistoryElt: String = "hx-history-elt"

    /**
     * Includes additional data in requests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Include)
     */
    public const val Include: String = "hx-include"

    /**
     * Specifies the element to put the htmx-request class on during the request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Indicator)
     */
    public const val Indicator: String = "hx-indicator"

    /**
     * Controls and enables automatic attribute inheritance for child nodes if it has been disabled by default.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Inherit)
     */
    public const val Inherit: String = "hx-inherit"

    /**
     * Filters the parameters that will be submitted with a request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Params)
     */
    public const val Params: String = "hx-params"

    /**
     * Issues a PATCH to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Patch)
     */
    public const val Patch: String = "hx-patch"

    /**
     * Specifies elements to keep unchanged between requests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Preserve)
     */
    public const val Preserve: String = "hx-preserve"

    /**
     * Shows a prompt() before submitting a request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Prompt)
     */
    public const val Prompt: String = "hx-prompt"

    /**
     * Issues a PUT to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Put)
     */
    public const val Put: String = "hx-put"

    /**
     * Replaces the URL in the browser location bar.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.ReplaceUrl)
     */
    public const val ReplaceUrl: String = "hx-replace-url"

    /**
     * Configures various aspects of the request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Request)
     */
    public const val Request: String = "hx-request"

    /**
     * Controls how requests made by different elements are synchronized.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Sync)
     */
    public const val Sync: String = "hx-sync"

    /**
     * Forces elements to validate themselves before a request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Validate)
     */
    public const val Validate: String = "hx-validate"

    /**
     * Adds values dynamically to the parameters to submit with the request (deprecated, please use hx-vals).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.HxAttributeKeys.Vars)
     */
    public const val Vars: String = "hx-vars"
}

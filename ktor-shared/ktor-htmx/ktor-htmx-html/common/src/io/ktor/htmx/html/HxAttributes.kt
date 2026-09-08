/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.htmx.html

import io.ktor.htmx.*
import io.ktor.util.collections.*
import io.ktor.utils.io.*
import kotlinx.html.HtmlTagMarker
import kotlinx.html.impl.DelegatingMap
import kotlin.jvm.JvmInline

/**
 * Provides typed access to the HTMX (`hx-*`) attributes of this tag.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.hx)
 */
public val DelegatingMap.hx: HxAttributes get() = HxAttributes(this)

/**
 * Configures the HTMX (`hx-*`) attributes of this tag.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.hx)
 */
public inline fun DelegatingMap.hx(block: HxAttributes.() -> Unit) {
    hx.block()
}

/**
 * Typed accessors for the HTMX (`hx-*`) attributes understood by the htmx.org client library.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes)
 *
 * @see [Official documentation](https://htmx.org/reference/#attributes-additional)
 */
@HtmlTagMarker
@OptIn(InternalAPI::class)
public class HxAttributes(override val map: DelegatingMap) : StringMapDelegate {
    /**
     * Issues a GET to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.get)
     */
    public var get: String? by HxAttributeKeys.Get

    /**
     * Issues a POST to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.post)
     */
    public var post: String? by HxAttributeKeys.Post

    /**
     * Pushes a URL into the browser location bar to create history.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.pushUrl)
     */
    public var pushUrl: String? by HxAttributeKeys.PushUrl

    /**
     * Selects content to swap in from a response.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.select)
     */
    public var select: String? by HxAttributeKeys.Select

    /**
     * Selects content to swap in from a response, somewhere other than the target (out of band).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.selectOob)
     */
    public var selectOob: String? by HxAttributeKeys.SelectOob

    /**
     * Controls how content will swap in (outerHTML, beforeend, afterend, …).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.swap)
     */
    public var swap: String? by HxAttributeKeys.Swap

    /**
     * Marks element to swap in from a response (out of band).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.swapOob)
     */
    public var swapOob: String? by HxAttributeKeys.SwapOob

    /**
     * Specifies the target element to be swapped.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.target)
     */
    public var target: String? by HxAttributeKeys.Target

    /**
     * Specifies the event that triggers the request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.trigger)
     */
    public var trigger: String? by HxAttributeKeys.Trigger

    /**
     * Adds values to submit with the request (JSON format).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.vals)
     */
    public var vals: String? by HxAttributeKeys.Vals

    /**
     * Adds progressive enhancement for links and forms.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.boost)
     */
    public var boost: Boolean? by HxAttributeKeys.Boost.asBoolean()

    /**
     * Shows a confirm() dialog before issuing a request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.confirm)
     */
    public var confirm: String? by HxAttributeKeys.Confirm

    /**
     * Issues a DELETE to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.delete)
     */
    public var delete: String? by HxAttributeKeys.Delete

    /**
     * Disables htmx processing for the given node and any children nodes.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.disable)
     */
    public var disable: Boolean? by HxAttributeKeys.Disable.asPresenceBoolean()

    /**
     * Adds the disabled attribute to the specified elements while a request is in flight.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.disabledElt)
     */
    public var disabledElt: String? by HxAttributeKeys.DisabledElt

    /**
     * Controls and disables automatic attribute inheritance for child nodes.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.disinherit)
     */
    public var disinherit: String? by HxAttributeKeys.Disinherit

    /**
     * Changes the request encoding type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.encoding)
     */
    public var encoding: String? by HxAttributeKeys.Encoding

    /**
     * Extensions to use for this element.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.ext)
     */
    public var ext: String? by HxAttributeKeys.Ext

    /**
     * Prevents sensitive data from being saved to the history cache.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.history)
     */
    public var history: String? by HxAttributeKeys.History

    /**
     * Specifies the element to snapshot and restore during history navigation.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.historyElt)
     */
    public var historyElt: String? by HxAttributeKeys.HistoryElt

    /**
     * Includes additional data in requests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.include)
     */
    public var include: String? by HxAttributeKeys.Include

    /**
     * Specifies the element to put the htmx-request class on during the request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.indicator)
     */
    public var indicator: String? by HxAttributeKeys.Indicator

    /**
     * Controls and enables automatic attribute inheritance for child nodes if it has been disabled by default.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.inherit)
     */
    public var inherit: String? by HxAttributeKeys.Inherit

    /**
     * Filters the parameters that will be submitted with a request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.params)
     */
    public var params: String? by HxAttributeKeys.Params

    /**
     * Issues a PATCH to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.patch)
     */
    public var patch: String? by HxAttributeKeys.Patch

    /**
     * Specifies elements to keep unchanged between requests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.preserve)
     */
    public var preserve: Boolean? by HxAttributeKeys.Preserve.asBoolean()

    /**
     * Shows a prompt() before submitting a request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.prompt)
     */
    public var prompt: String? by HxAttributeKeys.Prompt

    /**
     * Issues a PUT to the specified URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.put)
     */
    public var put: String? by HxAttributeKeys.Put

    /**
     * Replaces the URL in the browser location bar.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.replaceUrl)
     */
    public var replaceUrl: String? by HxAttributeKeys.ReplaceUrl

    /**
     * Configures various aspects of the request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.request)
     */
    public var request: String? by HxAttributeKeys.Request

    /**
     * Controls how requests made by different elements are synchronized.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.sync)
     */
    public var sync: String? by HxAttributeKeys.Sync

    /**
     * Forces elements to validate themselves before a request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.validate)
     */
    public var validate: Boolean? by HxAttributeKeys.Validate.asBoolean()

    /**
     * Adds values dynamically to the parameters to submit with the request (deprecated, please use hx-vals).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.vars)
     */
    public var vars: String? by HxAttributeKeys.Vars

    /**
     * Provides typed access to the `hx-on:*` event handler attributes of this tag.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.on)
     */
    public val on: On
        get() = On(map)

    /**
     * Handles the given [event] with the inline [script] on this tag.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.on)
     */
    public fun on(event: String, script: String) {
        map["hx-on::$event"] = script
    }

    /**
     * Typed access to the `hx-on:*` event handler attributes of a tag.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.On)
     */
    @JvmInline
    public value class On(private val attributes: MutableMap<String, String>) {
        /**
         * Sets the inline [script] to run when [event] fires, or removes the handler if [script] is `null`.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.On.set)
         */
        public operator fun set(event: String, script: String?) {
            if (script == null) {
                attributes.remove("${HxAttributeKeys.On}::$event")
            } else {
                attributes["${HxAttributeKeys.On}::$event"] = script
            }
        }

        /**
         * Returns the inline script configured for [event], if any.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.htmx.html.HxAttributes.On.get)
         */
        public operator fun get(event: String): String? = attributes["${HxAttributeKeys.On}:$event"]
    }
}

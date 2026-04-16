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

@ExperimentalKtorApi
public val DelegatingMap.hx: HxAttributes get() = HxAttributes(this)

@ExperimentalKtorApi
public inline fun DelegatingMap.hx(block: HxAttributes.() -> Unit) {
    hx.block()
}

@ExperimentalKtorApi
@HtmlTagMarker
@OptIn(InternalAPI::class)
public class HxAttributes(override val map: DelegatingMap) : StringMapDelegate {
    public var get: String? by HxAttributeKeys.Get
    public var post: String? by HxAttributeKeys.Post
    public var pushUrl: String? by HxAttributeKeys.PushUrl
    public var select: String? by HxAttributeKeys.Select
    public var selectOob: String? by HxAttributeKeys.SelectOob
    public var swap: String? by HxAttributeKeys.Swap
    public var swapOob: String? by HxAttributeKeys.SwapOob
    public var target: String? by HxAttributeKeys.Target
    public var trigger: String? by HxAttributeKeys.Trigger
    public var vals: String? by HxAttributeKeys.Vals
    public var boost: Boolean? by HxAttributeKeys.Boost.asBoolean()
    public var confirm: String? by HxAttributeKeys.Confirm
    public var delete: String? by HxAttributeKeys.Delete
    public var disable: Boolean? by HxAttributeKeys.Disable.asPresenceBoolean()
    public var disabledElt: String? by HxAttributeKeys.DisabledElt
    public var disinherit: String? by HxAttributeKeys.Disinherit
    public var encoding: String? by HxAttributeKeys.Encoding
    public var ext: String? by HxAttributeKeys.Ext
    public var history: String? by HxAttributeKeys.History
    public var historyElt: String? by HxAttributeKeys.HistoryElt
    public var include: String? by HxAttributeKeys.Include
    public var indicator: String? by HxAttributeKeys.Indicator
    public var inherit: String? by HxAttributeKeys.Inherit
    public var params: String? by HxAttributeKeys.Params
    public var patch: String? by HxAttributeKeys.Patch
    public var preserve: Boolean? by HxAttributeKeys.Preserve.asBoolean()
    public var prompt: String? by HxAttributeKeys.Prompt
    public var put: String? by HxAttributeKeys.Put
    public var replaceUrl: String? by HxAttributeKeys.ReplaceUrl
    public var request: String? by HxAttributeKeys.Request
    public var sync: String? by HxAttributeKeys.Sync
    public var validate: Boolean? by HxAttributeKeys.Validate.asBoolean()
    public var vars: String? by HxAttributeKeys.Vars

    public val on: On
        get() = On(map)

    public fun on(event: String, script: String) {
        map["hx-on::$event"] = script
    }

    @JvmInline
    public value class On(private val attributes: MutableMap<String, String>) {
        public operator fun set(event: String, script: String?) {
            if (script == null) {
                attributes.remove("${HxAttributeKeys.On}::$event")
            } else {
                attributes["${HxAttributeKeys.On}::$event"] = script
            }
        }

        public operator fun get(event: String): String? = attributes["${HxAttributeKeys.On}:$event"]
    }
}

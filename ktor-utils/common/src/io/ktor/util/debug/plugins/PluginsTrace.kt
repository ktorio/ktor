/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.debug.plugins

import kotlin.coroutines.*

/**
 * Contains information of all the plugins that have been executed during the current call.
 * Is used in Intellij Idea debugger to show plugin execution order.
 */
public data class PluginsTrace(
    /**
     * Plugin name.
     */
    val eventOrder: MutableList<PluginTraceElement> = mutableListOf()
) : AbstractCoroutineContextElement(PluginsTrace) {

    /**
     * Key for [PluginsTrace] instance in the coroutine context.
     */
    public companion object Key : CoroutineContext.Key<PluginsTrace>

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String = "PluginsTrace(${eventOrder.joinToString()})"
}

/**
 * Contains information about the plugin and handler (onCall, onReceive, and so on) that is currently being executed.
 * Is used in Intellij Idea debugger to show plugin execution order.
 * */
public data class PluginTraceElement(val pluginName: String, val handler: String, val event: PluginEvent) {
    public enum class PluginEvent {
        STARTED,
        FINISHED
    }
}

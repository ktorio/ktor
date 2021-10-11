package io.ktor.server.application.plugins.api.debug

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Name of the Ktor plugin that is currently being invoked. This name is used in debugging mode.
 */
public data class PluginName(
    /**
     * Plugin name.
     */
    val pluginName: String
) : AbstractCoroutineContextElement(PluginName) {
    /**
     * Key for [PluginName] instance in the coroutine context.
     */
    public companion object Key : CoroutineContext.Key<PluginName>

    /**
     * Returns a string representation of the object.
     */
    override fun toString(): String = "PluginName($pluginName)"
}

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * Defines an installable Application Plugin
 * @param TPipeline is the type of the pipeline this plugin is compatible with
 * @param TConfiguration is the type for the configuration object for this Plugin
 * @param TPlugin is the type for the instance of the Plugin object
 */
@Suppress("AddVarianceModifier")
public interface ApplicationPlugin<
    in TPipeline : Pipeline<*, ApplicationCall>,
    out TConfiguration : Any,
    TPlugin : Any> {
    /**
     * Unique key that identifies a plugin
     */
    public val key: AttributeKey<TPlugin>

    /**
     * Plugin installation script
     */
    public fun install(pipeline: TPipeline, configure: TConfiguration.() -> Unit): TPlugin
}

internal val pluginRegistryKey = AttributeKey<Attributes>("ApplicationPluginRegistry")

/**
 * Gets plugin instance for this pipeline, or fails with [MissingApplicationPluginException] if the plugin is not installed
 * @throws MissingApplicationPluginException
 * @param plugin application plugin to lookup
 * @return an instance of plugin
 */
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.plugin(plugin: ApplicationPlugin<A, B, F>): F {
    return attributes[pluginRegistryKey].getOrNull(plugin.key)
        ?: throw MissingApplicationPluginException(plugin.key)
}

/**
 * Returns plugin instance for this pipeline, or null if plugin is not installed
 */
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.pluginOrNull(plugin: ApplicationPlugin<A, B, F>): F? {
    return attributes.getOrNull(pluginRegistryKey)?.getOrNull(plugin.key)
}

/**
 * Installs [plugin] into this pipeline, if it is not yet installed
 */
public fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> P.install(
    plugin: ApplicationPlugin<P, B, F>,
    configure: B.() -> Unit = {}
): F {
    val registry = attributes.computeIfAbsent(pluginRegistryKey) { Attributes(true) }
    val installedPlugin = registry.getOrNull(plugin.key)
    when (installedPlugin) {
        null -> {
            try {
                val installed = plugin.install(this, configure)
                registry.put(plugin.key, installed)
                // environment.log.trace("`${plugin.name}` plugin was installed successfully.")
                return installed
            } catch (t: Throwable) {
                // environment.log.error("`${plugin.name}` plugin failed to install.", t)
                throw t
            }
        }
        plugin -> {
            // environment.log.warning("`${plugin.name}` plugin is already installed")
            return installedPlugin
        }
        else -> {
            throw DuplicateApplicationPluginException(
                "Conflicting application plugin is already installed with the same key as `${plugin.key.name}`"
            )
        }
    }
}

/**
 * Uninstalls all plugins from the pipeline
 */
@Deprecated(
    "This method is misleading and will be removed. " +
        "If you have use case that requires this functionaity, please add it in KTOR-2696"
)
public fun <A : Pipeline<*, ApplicationCall>> A.uninstallAllPlugins() {
    val registry = attributes.computeIfAbsent(pluginRegistryKey) { Attributes(true) }
    registry.allKeys.forEach {
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        uninstallPlugin(it as AttributeKey<Any>)
    }
}

/**
 * Uninstalls [plugin] from the pipeline
 */
@Suppress("DEPRECATION")
@Deprecated(
    "This method is misleading and will be removed. " +
        "If you have use case that requires this functionaity, please add it in KTOR-2696"
)
public fun <A : Pipeline<*, ApplicationCall>, B : Any, F : Any> A.uninstall(
    plugin: ApplicationPlugin<A, B, F>
): Unit = uninstallPlugin(plugin.key)

/**
 * Uninstalls plugin specified by [key] from the pipeline
 */
@Deprecated(
    "This method is misleading and will be removed. " +
        "If you have use case that requires this functionaity, please add it in KTOR-2696"
)
public fun <A : Pipeline<*, ApplicationCall>, F : Any> A.uninstallPlugin(key: AttributeKey<F>) {
    val registry = attributes.getOrNull(pluginRegistryKey) ?: return
    val instance = registry.getOrNull(key) ?: return
    if (instance is Closeable) {
        instance.close()
    }
    registry.remove(key)
}

/**
 * Thrown when Application Plugin has been attempted to be installed with the same key as already installed Plugin
 */
public class DuplicateApplicationPluginException(message: String) : Exception(message)

/**
 * Thrown when Application Plugin has been attempted to be accessed but has not been installed before
 * @param key application plugin's attribute key
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class MissingApplicationPluginException(
    public val key: AttributeKey<*>
) : IllegalStateException(), CopyableThrowable<MissingApplicationPluginException> {
    override val message: String get() = "Application plugin ${key.name} is not installed"

    override fun createCopy(): MissingApplicationPluginException? = MissingApplicationPluginException(key).also {
        it.initCause(this)
    }
}

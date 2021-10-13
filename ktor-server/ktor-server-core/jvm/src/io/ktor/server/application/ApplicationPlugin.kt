/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * Defines an installable Plugin
 * @param TPipeline is the type of the pipeline this plugin is compatible with
 * @param TConfiguration is the configuration object type for this Plugin
 * @param TPlugin is the instance type of the Plugin object
 */
@Suppress("AddVarianceModifier")
public interface Plugin<
    in TPipeline : Pipeline<*, ApplicationCall>,
    out TConfiguration : Any,
    TPlugin : Any> {
    /**
     * Unique key that identifies a plugin
     */
    public val key: AttributeKey<TPlugin>
}

/**
 * Defines a Plugin that is installed into Application
 * @param TPipeline is the type of the pipeline this plugin is compatible with
 * @param TConfiguration is the configuration object type for this Plugin
 * @param TPlugin is the instance type of the Plugin object
 */
@Suppress("AddVarianceModifier")
public interface ApplicationPlugin<
    in TPipeline : Pipeline<*, ApplicationCall>,
    out TConfiguration : Any,
    TPlugin : Any> : Plugin<TPipeline, TConfiguration, TPlugin> {

    /**
     * Plugin installation script
     */
    public fun install(pipeline: TPipeline, configure: TConfiguration.() -> Unit): TPlugin
}

internal val pluginRegistryKey = AttributeKey<Attributes>("ApplicationPluginRegistry")

/**
 * Returns existing plugin registry or register and returns a new one
 */
public val <A : Pipeline<*, ApplicationCall>> A.pluginRegistry: Attributes
    get() = attributes.computeIfAbsent(pluginRegistryKey) { Attributes(true) }

/**
 * Gets a plugin instance for this pipeline, or fails with [MissingApplicationPluginException]
 * if the plugin is not installed.
 * @throws MissingApplicationPluginException
 * @param plugin [Plugin] to lookup
 * @return an instance of a plugin
 */
public fun <A : Pipeline<*, ApplicationCall>, F : Any> A.plugin(plugin: Plugin<*, *, F>): F {
    return when (this) {
        is Route -> findPluginInRoute(plugin)
        else -> pluginOrNull(plugin)
    } ?: throw MissingApplicationPluginException(plugin.key)
}

/**
 * Returns plugin instance for this pipeline, or null if plugin is not installed
 */
public fun <A : Pipeline<*, ApplicationCall>, F : Any> A.pluginOrNull(plugin: Plugin<*, *, F>): F? {
    return pluginRegistry.getOrNull(plugin.key)
}

/**
 * Installs [plugin] into this pipeline, if it is not yet installed
 */
public fun <P : Pipeline<*, ApplicationCall>, B : Any, F : Any> P.install(
    plugin: ApplicationPlugin<P, B, F>,
    configure: B.() -> Unit = {}
): F {
    val registry = pluginRegistry
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
    pluginRegistry.allKeys.forEach {
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
    plugin: Plugin<A, B, F>
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

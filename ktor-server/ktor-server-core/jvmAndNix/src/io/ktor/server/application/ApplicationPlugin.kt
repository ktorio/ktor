/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.application

import io.ktor.server.application.internal.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.native.concurrent.*

@DslMarker
internal annotation class PluginsDslMarker

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
     * A unique key that identifies a plugin
     */
    public val key: AttributeKey<TPlugin>

    /**
     * A plugin's installation script
     */
    public fun install(pipeline: TPipeline, configure: TConfiguration.() -> Unit): TPlugin
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
    TPlugin : Any> : Plugin<TPipeline, TConfiguration, TPlugin>

@SharedImmutable
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
    plugin: Plugin<P, B, F>,
    configure: B.() -> Unit = {}
): F {
    if (this is Route && plugin is RouteScopedPlugin) {
        return installIntoRoute(plugin, configure)
    }

    val registry = pluginRegistry
    return when (val installedPlugin = registry.getOrNull(plugin.key)) {
        null -> {
            try {
                val installed = plugin.install(this, configure)
                registry.put(plugin.key, installed)
                // environment.log.trace("`${plugin.name}` plugin was installed successfully.")
                installed
            } catch (t: Throwable) {
                // environment.log.error("`${plugin.name}` plugin failed to install.", t)
                throw t
            }
        }
        plugin -> {
            // environment.log.warning("`${plugin.name}` plugin is already installed")
            installedPlugin
        }
        else -> {
            throw DuplicatePluginException(
                "Please make sure that you use unique name for the plugin and don't install it twice. " +
                    "Conflicting application plugin is already installed with the same key as `${plugin.key.name}`"
            )
        }
    }
}

private fun <B : Any, F : Any> Route.installIntoRoute(
    plugin: RouteScopedPlugin<B, F>,
    configure: B.() -> Unit = {}
): F {
    if (pluginRegistry.getOrNull(plugin.key) != null) {
        throw DuplicatePluginException(
            "Please make sure that you use unique name for the plugin and don't install it twice. " +
                "Plugin `${plugin.key.name}` is already installed to the pipeline $this"
        )
    }
    if (application.pluginRegistry.getOrNull(plugin.key) != null) {
        throw DuplicatePluginException(
            "Installing RouteScopedPlugin to application and route is not supported. " +
                "Consider moving application level install to routing root."
        )
    }
    // we install plugin into fake pipeline and add interceptors manually
    // to avoid having multiple interceptors after pipelines are merged
    val fakePipeline = Route(parent, selector, developmentMode, environment)
    val installed = plugin.install(fakePipeline, configure)
    pluginRegistry.put(plugin.key, installed)

    mergePhases(fakePipeline)
    receivePipeline.mergePhases(fakePipeline.receivePipeline)
    sendPipeline.mergePhases(fakePipeline.sendPipeline)

    addAllInterceptors(fakePipeline, plugin, installed)
    receivePipeline.addAllInterceptors(fakePipeline.receivePipeline, plugin, installed)
    sendPipeline.addAllInterceptors(fakePipeline.sendPipeline, plugin, installed)

    return installed
}

private fun <B : Any, F : Any, TSubject, TContext, P : Pipeline<TSubject, TContext>> P.addAllInterceptors(
    fakePipeline: P,
    plugin: RouteScopedPlugin<B, F>,
    pluginInstance: F
) {
    items.forEach { phase ->
        fakePipeline.interceptorsForPhase(phase)
            .forEach { interceptor ->
                intercept(phase) { subject ->
                    val call = context
                    if (call is RoutingApplicationCall && call.route.findPluginInRoute(plugin) == pluginInstance) {
                        interceptor(this, subject)
                    }
                }
            }
    }
}

/**
 * Installs [plugin] into this pipeline, if it is not yet installed
 */
@Deprecated(
    "Installing ApplicationPlugin into routing may lead to unexpected behaviour. " +
        "Consider moving installation to the application level " +
        "or migrate this plugin to `RouteScopedPlugin` to support installing into route."
)
public fun <P : Route, B : Any, F : Any> P.install(
    plugin: ApplicationPlugin<P, B, F>,
    configure: B.() -> Unit = {}
): F {
    return install(plugin as Plugin<P, B, F>, configure)
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
 * Thrown on an attempt to install the plugin with the same key as for the already installed plugin
 */
@Deprecated(
    message = "Please use DuplicatePluginException instead",
    replaceWith = ReplaceWith("DuplicatePluginException")
)
public open class DuplicateApplicationPluginException(message: String) : Exception(message)

/**
 * Thrown on an attempt to install the plugin with the same key as for the already installed plugin
 */
@Suppress("DEPRECATION")
public class DuplicatePluginException(message: String) : DuplicateApplicationPluginException(message)

/**
 * Thrown on an attempt to access the plugin that is not yet installed
 * @param key application plugin's attribute key
 */
@OptIn(ExperimentalCoroutinesApi::class)
public class MissingApplicationPluginException(
    public val key: AttributeKey<*>
) : IllegalStateException(), CopyableThrowable<MissingApplicationPluginException> {
    override val message: String get() = "Application plugin ${key.name} is not installed"

    override fun createCopy(): MissingApplicationPluginException = MissingApplicationPluginException(key).also {
        it.initCauseBridge(this)
    }
}

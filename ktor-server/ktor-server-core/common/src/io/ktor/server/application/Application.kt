/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A builder for [ServerConfig].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ServerConfigBuilder)
 */
@Suppress("ktlint:standard:no-consecutive-comments")
// TODO KTOR-8809: Uncomment the annotation
// @KtorDsl
public class ServerConfigBuilder(
    public val environment: ApplicationEnvironment
) {

    internal val modules: MutableList<suspend Application.() -> Unit> = mutableListOf()

    /**
     * Paths to wait for application reload.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ServerConfigBuilder.watchPaths)
     */
    public var watchPaths: List<String> = defaultWatchPaths()

    /**
     * Application's root path (prefix, context path in servlet container).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ServerConfigBuilder.rootPath)
     */
    public var rootPath: String = ""

    /**
     * Indicates whether development mode is enabled.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ServerConfigBuilder.developmentMode)
     */
    public var developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE

    /**
     * Parent coroutine context for an application.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ServerConfigBuilder.parentCoroutineContext)
     */
    public var parentCoroutineContext: CoroutineContext = EmptyCoroutineContext

    /**
     * Installs an application module.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ServerConfigBuilder.module)
     */
    @Deprecated("Replaced with suspend function parameter", level = DeprecationLevel.HIDDEN)
    public fun module(body: Application.() -> Unit) {
        modules.add(body)
    }

    /**
     * Installs an application module.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ServerConfigBuilder.module)
     */
    public fun module(body: suspend Application.() -> Unit) {
        modules.add(body)
    }

    internal fun build(): ServerConfig =
        ServerConfig(environment, modules.toList(), watchPaths, rootPath, developmentMode, parentCoroutineContext)
}

internal expect fun defaultWatchPaths(): List<String>

/**
 * Core configuration for a running server.
 * Contains modules, paths, and environment details.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.ServerConfig)
 */
public class ServerConfig internal constructor(
    public val environment: ApplicationEnvironment,
    internal val modules: List<suspend Application.() -> Unit>,
    internal val watchPaths: List<String>,
    public val rootPath: String,
    public val developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE,
    parentCoroutineContext: CoroutineContext
) {
    private val bridge = ApplicationRootConfigBridge(this, parentCoroutineContext)
    public val parentCoroutineContext: CoroutineContext = bridge.parentCoroutineContext
}

/**
 * Creates an [ServerConfig] instance.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.serverConfig)
 */
public fun serverConfig(
    environment: ApplicationEnvironment = applicationEnvironment {},
    block: ServerConfigBuilder.() -> Unit = {}
): ServerConfig {
    return ServerConfigBuilder(environment).apply(block).build()
}

/**
 * Represents configured and running web application, capable of handling requests.
 * It is also the application coroutine scope that is cancelled immediately at application stop so useful
 * for launching background coroutines.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.Application)
 */
@KtorDsl
public class Application internal constructor(
    environment: ApplicationEnvironment,
    developmentMode: Boolean,
    public var rootPath: String,
    public val monitor: Events,
    public val parentCoroutineContext: CoroutineContext,
    private val engineProvider: () -> ApplicationEngine
) : ApplicationCallPipeline(developmentMode, environment), CoroutineScope {

    private val applicationJob = SupervisorJob(parentCoroutineContext[Job])

    public val engine: ApplicationEngine get() = engineProvider()

    override val coroutineContext: CoroutineContext = parentCoroutineContext + applicationJob

    /**
     * Cancels the application job without waiting for join.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.Application.dispose)
     */
    @Deprecated(
        replaceWith = ReplaceWith("disposeAndJoin()"),
        level = DeprecationLevel.WARNING,
        message = "Use disposeAndJoin() instead."
    )
    @Suppress("DEPRECATION_ERROR")
    public fun dispose() {
        applicationJob.cancel()
        uninstallAllPlugins()
    }

    /**
     * Called by [ApplicationEngine] when [Application] is terminated.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.Application.disposeAndJoin)
     */
    @InternalAPI
    @Suppress("DEPRECATION_ERROR")
    public suspend fun disposeAndJoin() {
        applicationJob.cancelAndJoin()
        uninstallAllPlugins()
    }
}

/**
 * Convenience property to access log from application
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.log)
 */
public val Application.log: Logger get() = environment.log

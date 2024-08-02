/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

@Deprecated(message = "Renamed to ServerPropertiesBuilder", replaceWith = ReplaceWith("ServerPropertiesBuilder"))
public typealias ApplicationPropertiesBuilder = ServerParametersBuilder

/**
 * A builder for [ServerParameters].
 */
public class ServerParametersBuilder(
    public val environment: ServerEnvironment
) {

    internal val modules: MutableList<Server.() -> Unit> = mutableListOf()

    /**
     * Paths to wait for application reload.
     */
    public var watchPaths: List<String> = listOf(WORKING_DIRECTORY_PATH)

    /**
     * Application's root path (prefix, context path in servlet container).
     */
    public var rootPath: String = ""

    /**
     * Indicates whether development mode is enabled.
     */
    public var developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE

    /**
     * Parent coroutine context for an application.
     */
    public var parentCoroutineContext: CoroutineContext = EmptyCoroutineContext

    /**
     * Installs an application module.
     */
    public fun module(body: Server.() -> Unit) {
        modules.add(body)
    }

    internal fun build(): ServerParameters =
        ServerParameters(environment, modules, watchPaths, rootPath, developmentMode, parentCoroutineContext)
}

@Deprecated(message = "Renamed to ServerProperties", replaceWith = ReplaceWith("ServerProperties"))
public typealias ApplicationProperties = ServerParameters

/**
 * An application config with which the application is running.
 */
public class ServerParameters internal constructor(
    public val environment: ServerEnvironment,
    internal val modules: MutableList<Server.() -> Unit>,
    internal val watchPaths: List<String>,
    public val rootPath: String,
    public val developmentMode: Boolean = PlatformUtils.IS_DEVELOPMENT_MODE,
    parentCoroutineContext: CoroutineContext
) {
    private val bridge = ServerParametersBridge(this, parentCoroutineContext)
    public val parentCoroutineContext: CoroutineContext = bridge.parentCoroutineContext
}

/**
 * Creates an [ServerParameters] instance.
 */
public fun serverParams(
    environment: ServerEnvironment = serverEnvironment {},
    block: ServerParametersBuilder.() -> Unit = {}
): ServerParameters {
    return ServerParametersBuilder(environment).apply(block).build()
}

@Deprecated(message = "Renamed to Server", replaceWith = ReplaceWith("Server"))
public typealias Application = Server

/**
 * Represents configured and running web application, capable of handling requests.
 * It is also the application coroutine scope that is cancelled immediately at application stop so useful
 * for launching background coroutines.
 */
@KtorDsl
public class Server internal constructor(
    environment: ServerEnvironment,
    developmentMode: Boolean,
    public var rootPath: String,
    public val monitor: Events,
    public val parentCoroutineContext: CoroutineContext,
    private val engineProvider: () -> ServerEngine
) : ServerCallPipeline(developmentMode, environment), CoroutineScope {

    private val applicationJob = SupervisorJob(parentCoroutineContext[Job])

    public val engine: ServerEngine get() = engineProvider()

    override val coroutineContext: CoroutineContext = parentCoroutineContext + applicationJob

    /**
     * Called by [ServerEngine] when [Server] is terminated.
     */
    @Suppress("DEPRECATION_ERROR")
    public fun dispose() {
        applicationJob.cancel()
        uninstallAllPlugins()
    }
}

/**
 * Convenience property to access log from application
 */
public val Server.log: Logger get() = environment.log

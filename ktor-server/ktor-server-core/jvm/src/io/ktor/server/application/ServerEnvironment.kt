/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.events.*
import io.ktor.server.config.*
import io.ktor.util.logging.*
import kotlin.coroutines.*

/**
 * Represents an environment in which [Server] runs
 */
public actual interface ServerEnvironment {

    /**
     * [ClassLoader] used to load application.
     *
     * Useful for various reflection-based services, like dependency injection.
     */
    public val classLoader: ClassLoader

    /**
     * Instance of [Logger] to be used for logging.
     */
    public actual val log: Logger

    /**
     * Configuration for the [Server]
     */
    public actual val config: ServerConfig

    /**
     * Provides events on Application lifecycle
     */
    @Deprecated(
        message = "Moved to Application",
        replaceWith = ReplaceWith("EmbeddedServer.monitor", "io.ktor.server.engine.EmbeddedServer"),
        level = DeprecationLevel.WARNING,
    )
    public actual val monitor: Events
}

internal actual class ServerParametersBridge actual constructor(
    serverParameters: ServerParameters,
    parentCoroutineContext: CoroutineContext,
) {
    actual val parentCoroutineContext: CoroutineContext = when {
        serverParameters.developmentMode && serverParameters.watchPaths.isNotEmpty() ->
            parentCoroutineContext + ClassLoaderAwareContinuationInterceptor

        else -> parentCoroutineContext
    }
}

private object ClassLoaderAwareContinuationInterceptor : ContinuationInterceptor {
    override val key: CoroutineContext.Key<*> =
        object : CoroutineContext.Key<ClassLoaderAwareContinuationInterceptor> {}

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        val classLoader = Thread.currentThread().contextClassLoader
        return object : Continuation<T> {
            override val context: CoroutineContext = continuation.context

            override fun resumeWith(result: Result<T>) {
                Thread.currentThread().contextClassLoader = classLoader
                continuation.resumeWith(result)
            }
        }
    }
}

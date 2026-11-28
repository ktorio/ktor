/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlin.coroutines.*

/**
 * Handles all uncaught exceptions and logs errors with the specified [logger]
 * ignoring [CancellationException] and [IOException].
 *
 * When it is created with an application provider, the application's coroutine failure handler
 * (see [setCoroutineFailureHandler]) gets every failure except [CancellationException] first,
 * including [IOException].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.DefaultUncaughtExceptionHandler)
 */
public class DefaultUncaughtExceptionHandler(
    private val logger: () -> Logger
) : CoroutineExceptionHandler {
    private var application: (() -> Application)? = null

    public constructor(logger: Logger) : this({ logger })

    /**
     * Creates a handler that passes failures to the [application]'s coroutine failure handler before logging them.
     *
     * This constructor is intended for use by Ktor engines only.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.DefaultUncaughtExceptionHandler.DefaultUncaughtExceptionHandler)
     */
    @InternalAPI
    public constructor(logger: () -> Logger, application: () -> Application) : this(logger) {
        this.application = application
    }

    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler.Key

    @OptIn(InternalAPI::class)
    override fun handleException(context: CoroutineContext, exception: Throwable) {
        if (exception is CancellationException) return
        // The provider throws while the application isn't created yet
        val application = application?.let { provider -> runCatching { provider() }.getOrNull() }
        if (application?.handleCoroutineFailure(context, exception) == true) return
        if (exception is IOException) return

        val coroutineName = context[CoroutineName] ?: context.toString()

        logger().error("Unhandled exception caught for $coroutineName", exception)
    }
}

/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private val CoroutineFailureHandlerKey =
    AttributeKey<(CoroutineContext, Throwable) -> Boolean>("CoroutineFailureHandler")

/**
 * Sets the [handler] for failures of coroutines that are rooted in this application or in its engine.
 * The handler returns `true` when it handled the failure, and `false` to fall back to the default handling.
 *
 * This function is intended for use by Ktor plugins only.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.setCoroutineFailureHandler)
 */
@InternalAPI
public fun Application.setCoroutineFailureHandler(
    handler: (context: CoroutineContext, exception: Throwable) -> Boolean
) {
    attributes.put(CoroutineFailureHandlerKey, handler)
}

/**
 * Passes a failure of a coroutine to the handler set with [setCoroutineFailureHandler].
 *
 * This function is intended for use by Ktor engines and plugins only.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.application.handleCoroutineFailure)
 *
 * @return `true` if the handler handled the failure, `false` if there is no handler or it didn't handle the failure.
 */
@InternalAPI
public fun Application.handleCoroutineFailure(context: CoroutineContext, exception: Throwable): Boolean =
    attributes.getOrNull(CoroutineFailureHandlerKey)?.invoke(context, exception) ?: false

/**
 * Gives failures of application coroutines to the handler set with [setCoroutineFailureHandler] first.
 * The rest go to the [parent] handler from `parentCoroutineContext`, or to the platform default.
 */
@OptIn(InternalAPI::class)
internal class ApplicationCoroutineExceptionHandler(
    private val application: Application,
    private val parent: CoroutineExceptionHandler?,
) : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        if (application.handleCoroutineFailure(context, exception)) return
        if (parent != null) return parent.handleException(context, exception)
        handleUnhandledCoroutineFailure(application, context, exception)
    }
}

internal expect fun handleUnhandledCoroutineFailure(
    application: Application,
    context: CoroutineContext,
    exception: Throwable,
)

/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.engine.internal.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal val IODispatcherKey: AttributeKey<CoroutineDispatcher> = AttributeKey("IODispatcher")

/**
 * The dispatcher used by this server for blocking operations,
 * as configured by [ApplicationEngine.Configuration.ioDispatcher].
 *
 * If the application was not created by an [EmbeddedServer], the platform default is returned.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.ioDispatcher)
 */
public val Application.ioDispatcher: CoroutineDispatcher
    get() = attributes.getOrNull(IODispatcherKey) ?: Dispatchers.IOBridge

internal fun Application.installIODispatcher(dispatcher: CoroutineDispatcher) {
    attributes.put(IODispatcherKey, dispatcher)
}

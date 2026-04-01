/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.statement

private const val USE_ENGINE_DISPATCHER_KEY: String = "io.ktor.client.statement.useEngineDispatcher"

@PublishedApi
internal actual val useEngineDispatcher: Boolean = System.getProperty(USE_ENGINE_DISPATCHER_KEY, "false").toBoolean()

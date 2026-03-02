/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.statement

/**
 * Controls whether HttpStatement.execute and body blocks are executed on the engine's dispatcher by default.
 * Can be overridden via system property `io.ktor.client.statement.useEngineDispatcher` on JVM.
 */
@PublishedApi
internal expect val useEngineDispatcher: Boolean

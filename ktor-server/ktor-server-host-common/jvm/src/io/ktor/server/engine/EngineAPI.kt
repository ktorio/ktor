/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

/**
 * API marked with this annotation is not intended to be used by end users
 * unless a custom server engine implementation is required
 */
@Experimental(Experimental.Level.WARNING)
annotation class EngineAPI

/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

/**
 * API marked with this annotation is not intended to be used by end users
 * unless a custom server engine implementation is required
 */
@Suppress("DEPRECATION")
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This is not general purpose API and should be only used in custom server engine implementations."
)
@Experimental(Experimental.Level.ERROR)
public annotation class EngineAPI

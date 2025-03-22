/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import kotlin.use as stdlibUse

public expect interface Closeable : AutoCloseable

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Use stdlib implementation instead. Remove import of this function")
public inline fun <T : Closeable?, R> T.use(block: (T) -> R): R = stdlibUse(block)

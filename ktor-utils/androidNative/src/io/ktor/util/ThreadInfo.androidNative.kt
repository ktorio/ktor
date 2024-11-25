/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(UnsafeNumber::class)
internal actual fun collectStack(thread: pthread_t): List<String> = emptyList()

internal actual fun setSignalHandler() = Unit

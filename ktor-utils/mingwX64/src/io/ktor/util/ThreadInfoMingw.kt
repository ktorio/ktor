/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import platform.posix.*

internal actual fun collectStack(thread: pthread_t): List<String> = emptyList()

internal actual fun setSignalHandler() = Unit

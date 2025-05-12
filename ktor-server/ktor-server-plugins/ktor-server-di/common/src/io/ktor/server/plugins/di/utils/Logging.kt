/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di.utils

internal fun Throwable.externalTraceLine(): String =
    externalTrace().substringBefore('\n').trim()

internal fun Throwable.externalTrace(): String =
    stackTraceToString()
        .substringAfterLast("io.ktor.server.plugins.di")
        .substringAfter('\n')

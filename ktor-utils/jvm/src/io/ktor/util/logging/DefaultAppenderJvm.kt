/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

/**
 * The default platform appender. Usually it prints to stdout.
 */
actual val Appender.Default: Appender
    get() = TextAppender.systemStreamsAppender


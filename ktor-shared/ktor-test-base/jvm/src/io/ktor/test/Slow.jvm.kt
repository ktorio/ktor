/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

actual fun includeSlowTests(): Boolean =
    System.getProperty("INCLUDE_SLOW_TESTS")?.toBoolean() ?: false

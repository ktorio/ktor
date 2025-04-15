/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

// Non-JVM platforms are not repeated in CI, so we'll always include
actual fun includeSlowTests(): Boolean = true

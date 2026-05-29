/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.test

import io.ktor.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * This value should be enough to run most of the tests. Individual tests can override it.
 * Making this value too small can cause false-positive failures, and if too large,
 * it can slow down detection of hanging tests or hide real performance issues.
 *
 * This value shouldn't be larger than Mocha's 'browserNoActivityTimeout' and 'browserDisconnectTimeout' options,
 * otherwise JS browser tests will fail with a misleading error message.
 */
val DEFAULT_TEST_TIMEOUT: Duration = 30.seconds

/**
 * Defaults to `1` on all platforms except for JVM.
 * On JVM retries are disabled as we use test-retry Gradle plugin instead.
 */
val DEFAULT_RETRIES: Int = if (PlatformUtils.IS_JVM) 0 else 1

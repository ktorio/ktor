/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.util.*

/**
 * Check that [block] completed with given type of root cause.
 */
@InternalAPI
expect inline fun <reified T : Throwable> assertFailsWithRootCause(block: () -> Unit)

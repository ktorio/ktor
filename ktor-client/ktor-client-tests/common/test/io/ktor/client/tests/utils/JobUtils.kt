/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import kotlinx.coroutines.*

/**
 * Utils function returning all active jobs in the hierarchy.
 */
internal fun Job.getActiveChildren(): Sequence<Job> = sequence {
    for (child in children) {
        if (child.isActive) {
            yield(child)
        }

        yieldAll(child.getActiveChildren())
    }
}

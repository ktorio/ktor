/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.test.base

import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

class TestInfo(val threadId: Int, val attempt: Int) : CoroutineContext.Element {
    override val key = TestInfo

    companion object : CoroutineContext.Key<TestInfo>
}

val CoroutineScope.testInfo: TestInfo?
    get() = coroutineContext[TestInfo]

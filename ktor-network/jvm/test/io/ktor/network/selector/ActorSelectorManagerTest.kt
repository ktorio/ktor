/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import io.ktor.test.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class ActorSelectorManagerTest {

    @Test
    fun `selectable is closed`() = runTest {
        val selectable = mockkSelectable(SelectInterest.READ, isClosed = true)

        withSelectorManager { manager ->
            assertFailsWith<IOException> {
                manager.select(selectable, SelectInterest.READ)
            }
        }
    }

    @Test
    fun `select on wrong interest`() = runTest {
        val selectable = mockkSelectable(SelectInterest.READ, isClosed = false)

        withSelectorManager { manager ->
            assertFailsWith<IllegalStateException> {
                manager.select(selectable, SelectInterest.WRITE)
            }
        }
    }

    @Test
    fun `parent job cancellation closes selection manager`() = runTest(timeout = 1.seconds) {
        val parent = Job(coroutineContext.job)

        withSelectorManager(Dispatchers.Unconfined + parent) {
            parent.cancelAndJoin()
        }
    }
}

@Suppress("SameParameterValue")
private fun mockkSelectable(interest: SelectInterest, isClosed: Boolean): Selectable = mockk {
    every { this@mockk.interestedOps } returns interest.flag
    every { this@mockk.isClosed } returns isClosed
}

private inline fun TestScope.withSelectorManager(
    coroutineContext: CoroutineContext = backgroundScope.coroutineContext,
    block: (ActorSelectorManager) -> Unit
) {
    ActorSelectorManager(coroutineContext).use(block)
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import kotlin.test.*

class CIOSustainabilityPosixTest : AbstractCIOSustainabilityCommonTest() {
    // The Native CIO full suite intermittently raises a PosixException from TCP bind/address resolution.
    @Test
    @Ignore
    override fun testJobsAreCancelledOnShutdown() = super.testJobsAreCancelledOnShutdown()
}

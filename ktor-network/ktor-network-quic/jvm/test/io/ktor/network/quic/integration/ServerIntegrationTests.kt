/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.integration

import kotlin.test.Test

class ServerIntegrationTests : ServerBaseIntegrationTest() {

    @Test
    fun kwikTest10() = integrationTest(messageSizeBytes = 10)

    @Test
    fun kwikTest100() = integrationTest(messageSizeBytes = 100)

//    @Test
//    fun kwikTest1000() = integrationTest(messageSizeBytes = 1000)
}

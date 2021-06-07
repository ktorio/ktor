/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.features

import io.ktor.features.*
import io.ktor.http.*
import io.mockk.*
import kotlin.test.*

class CORSTest {

    @Test
    fun originValidation() {
        val feature = CORS(
            CORS.Configuration().apply {
                allowSameOrigin = false
                anyHost()
            }
        )

        assertEquals(OriginCheckResult.OK, feature.checkOrigin("hyp-hen://host", dummyPoint()))
        assertEquals(OriginCheckResult.OK, feature.checkOrigin("plus+://host", dummyPoint()))
        assertEquals(OriginCheckResult.OK, feature.checkOrigin("do.t://host", dummyPoint()))
        assertEquals(OriginCheckResult.OK, feature.checkOrigin("digits11://host", dummyPoint()))

        assertEquals(OriginCheckResult.SkipCORS, feature.checkOrigin("a()://host", dummyPoint()))
        assertEquals(OriginCheckResult.SkipCORS, feature.checkOrigin("1abc://host", dummyPoint()))
    }

    private fun dummyPoint(): RequestConnectionPoint {
        return getConnectionPoint("scheme", "host", 12345)
    }

    private fun getConnectionPoint(scheme: String, host: String, port: Int): RequestConnectionPoint {
        val point = mockk<RequestConnectionPoint>()
        every { point.scheme } returns scheme
        every { point.host } returns host
        every { point.port } returns port
        return point
    }
}

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.features

import io.ktor.features.*
import io.ktor.http.*
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
        return object : RequestConnectionPoint {
            override val scheme: String
                get() = scheme
            override val version: String
                get() = TODO("Not yet implemented")
            override val port: Int
                get() = port
            override val host: String
                get() = host
            override val uri: String
                get() = TODO("Not yet implemented")
            override val method: HttpMethod
                get() = TODO("Not yet implemented")
            override val remoteHost: String
                get() = TODO("Not yet implemented")
        }
    }
}

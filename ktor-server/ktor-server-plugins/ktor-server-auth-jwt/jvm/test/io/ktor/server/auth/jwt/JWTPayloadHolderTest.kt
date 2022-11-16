/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.jwt

import com.auth0.jwt.impl.*
import kotlin.test.*

class JWTPayloadHolderTest {

    @Test
    fun noListClaim() {
        val payload = JWTParser().parsePayload("{}")
        val holder = object : JWTPayloadHolder(payload) {}
        assertEquals(emptyList(), holder.getListClaim("iss", String::class))
    }

    @Test
    fun claimListOfDifferentTypes() {
        val payload = JWTParser().parsePayload(
            """
            {
                "iss": ["issuer1", "issuer2"],
                "sub": [true, false],
                "aud": []
            }
            """.trimIndent()
        )
        val holder = object : JWTPayloadHolder(payload) {}

        assertEquals(listOf("issuer1", "issuer2"), holder.getListClaim("iss", String::class))
        assertEquals(listOf(true, false), holder.getListClaim("sub", Boolean::class))
        assertEquals(emptyList(), holder.getListClaim("aud", Any::class))
    }
}

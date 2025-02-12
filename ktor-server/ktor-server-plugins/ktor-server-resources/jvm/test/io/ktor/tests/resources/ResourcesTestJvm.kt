/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.resources

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.http.content.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourcesTestJvm {

    object BigDecimalSerializer : KSerializer<BigDecimal> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: BigDecimal) {
            val string = value.toString()
            encoder.encodeString(string)
        }

        override fun deserialize(decoder: Decoder): BigDecimal {
            val string = decoder.decodeString()
            return BigDecimal(string)
        }
    }

    @Resource("/")
    class LocationWithBigNumbers(
        @Serializable(with = BigDecimalSerializer::class)
        val bd: BigDecimal,
        @Serializable(with = BigDecimalSerializer::class)
        val bi: BigDecimal
    )

    @Test
    fun locationClassWithBigNumbers() = testResourcesApplication {
        val bd = BigDecimal("123456789012345678901234567890")
        val bi = BigDecimal("123456789012345678901234567890")

        routing {
            get<LocationWithBigNumbers> { location ->
                assertEquals(bd, location.bd)
                assertEquals(bi, location.bi)

                call.respondText(application.href(location))
            }
        }

        urlShouldBeHandled(
            LocationWithBigNumbers(bd, bi),
            "/?bd=123456789012345678901234567890&bi=123456789012345678901234567890"
        )
    }

    @Resource("/")
    class Home

    @Test
    fun testHomeResourceWithStaticResource() = testResourcesApplication {
        var executed = false
        routing {
            get<Home> {
                executed = true
                call.respondText("OK")
            }
            staticResources("/", "static")
        }

        client.get("/").let { response ->
            assertTrue(executed)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("OK", response.bodyAsText())
        }
    }
}

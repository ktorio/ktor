/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.serialization.kotlinx.json.DefaultJson
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class Payload(val value: String)

class ConverterTest {
    @Test
    fun returnsNullForEmptyChannelWithDelayedClose() = runTest {
        val channel = ByteChannel()
        launch {
            delay(200.milliseconds)
            channel.close()
        }

        val converter = KotlinxSerializationConverter(DefaultJson)
        val result = converter.deserialize(Charsets.UTF_8, typeInfo<Payload>(), channel)
        assertNull(result)
    }
}

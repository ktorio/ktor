/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.writeString
import kotlin.random.Random

internal fun generateRandomByteArray(minSize: Int, maxSize: Int = minSize + 1): ByteArray {
    require(minSize >= 0)
    require(minSize < maxSize) { "Failed to generate in range: [$minSize, $maxSize)" }

    val resultSize = Random.nextInt(minSize, maxSize)
    return Random.nextBytes(resultSize)
}

/**
 * Generates a [RawSource] over [text] that is deliberately not a [kotlinx.io.Source],
 * so that its size cannot be known upfront.
 */
internal fun rawSourceOf(text: String): RawSource = object : RawSource {
    private val buffer = Buffer().apply { writeString(text) }
    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long = buffer.readAtMostTo(sink, byteCount)
    override fun close() = buffer.close()
}

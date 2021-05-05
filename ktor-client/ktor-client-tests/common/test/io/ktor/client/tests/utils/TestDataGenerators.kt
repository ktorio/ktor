/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils

import kotlin.random.*

internal fun generateRandomByteArray(minSize: Int, maxSize: Int = minSize + 1): ByteArray {
    require(minSize >= 0)
    require(minSize < maxSize) { "Failed to generate in range: [$minSize, $maxSize)" }

    val resultSize = Random.nextInt(minSize, maxSize)
    return Random.nextBytes(resultSize)
}

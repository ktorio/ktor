/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*

internal expect class AbstractInputSharedState(
    head: ChunkBuffer,
    remaining: Long,
) {
    var head: ChunkBuffer

    var tailRemaining: Long

    var headMemory: Memory

    var headPosition: Int

    var headEndExclusive: Int
}

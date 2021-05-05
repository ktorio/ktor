// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*

internal actual class AbstractInputSharedState actual constructor(
    actual var head: ChunkBuffer,
    remaining: Long
) {
    actual var headMemory: Memory = head.memory

    actual var headPosition: Int = head.readPosition

    actual var headEndExclusive: Int = head.writePosition

    actual var tailRemaining: Long = remaining - (headEndExclusive - headPosition)
}

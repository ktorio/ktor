/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*

internal expect class AbstractOutputSharedState() {
    var head: ChunkBuffer?
    var tail: ChunkBuffer?

    var tailMemory: Memory
    var tailPosition: Int
    var tailEndExclusive: Int

    var tailInitialPosition: Int

    var chainedSize: Int
}

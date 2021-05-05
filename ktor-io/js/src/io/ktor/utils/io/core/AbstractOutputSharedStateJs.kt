// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*

internal actual class AbstractOutputSharedState {
    actual var head: ChunkBuffer? = null

    actual var tail: ChunkBuffer? = null

    actual var tailMemory: Memory = Memory.Empty

    actual var tailPosition: Int = 0

    actual var tailEndExclusive: Int = 0

    actual var tailInitialPosition: Int = 0

    actual var chainedSize: Int = 0
}

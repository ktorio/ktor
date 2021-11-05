/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core

import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*

internal actual val DefaultChunkedBufferPool: ObjectPool<ChunkBuffer> = DefaultBufferPool()

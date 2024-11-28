/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.core.internal

import io.ktor.utils.io.*
import kotlinx.io.*

@Deprecated(IO_DEPRECATION_MESSAGE, replaceWith = ReplaceWith("Buffer", "kotlinx.io"))
public typealias ChunkBuffer = kotlinx.io.Buffer

public val Buffer.writeRemaining: Int get() = Int.MAX_VALUE

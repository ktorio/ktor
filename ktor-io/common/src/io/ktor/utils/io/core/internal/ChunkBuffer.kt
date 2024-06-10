package io.ktor.utils.io.core.internal

import io.ktor.utils.io.*

@Deprecated(IO_DEPRECATION_MESSAGE, replaceWith = ReplaceWith("Buffer", "kotlinx.io"))
public typealias ChunkBuffer = kotlinx.io.Buffer

@Suppress("DEPRECATION")
public val ChunkBuffer.writeRemaining: Int get() = Int.MAX_VALUE

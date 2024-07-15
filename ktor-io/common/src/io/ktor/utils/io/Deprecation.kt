/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.io.*

internal const val IO_DEPRECATION_MESSAGE = """
    We're migrating to the new kotlinx-io library.
    This declaration is deprecated and will be removed in Ktor 4.0.0
    If you have any problems with migration, please contact us in 
    https://youtrack.jetbrains.com/issue/KTOR-6030/Migrate-to-new-kotlinx.io-library
    """

@Suppress("DEPRECATION")
public fun ByteReadPacket.readText(): String {
    return readString()
}

@Suppress("DEPRECATION")
@Deprecated("Use close() instead", ReplaceWith("close()"))
public fun BytePacketBuilder.release() {
    close()
}

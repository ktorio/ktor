/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.bits

import platform.posix.*

@PublishedApi
@SharedImmutable
internal actual val IS_PLATFORM_BIG_ENDIAN: Boolean = BYTE_ORDER == BIG_ENDIAN

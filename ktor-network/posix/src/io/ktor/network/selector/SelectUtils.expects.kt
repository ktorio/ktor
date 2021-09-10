/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import platform.posix.*
import kotlinx.cinterop.*

public typealias _sigset_t = UIntVarOf<UInt>

public expect fun pselect(
    __nfds: Int,
    __readfds: CValuesRef<fd_set>?,
    __writefds: CValuesRef<fd_set>?,
    __exceptfds: CValuesRef<fd_set>?,
    __timeout: CValuesRef<timespec>?,
    __sigmask: CValuesRef<_sigset_t>?
): Int

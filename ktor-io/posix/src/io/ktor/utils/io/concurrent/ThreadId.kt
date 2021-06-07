/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.concurrent

import kotlin.native.ThreadLocal
import kotlin.native.concurrent.*

internal class ThreadId {
    init {
        freeze()
    }

    companion object {
        val current get() = threadLocal
    }
}

@ThreadLocal
private val threadLocal: ThreadId = ThreadId()

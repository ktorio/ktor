/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.internal.*
import kotlin.native.concurrent.*

@DangerousInternalIoApi
public actual fun Any.preventFreeze() {
    ensureNeverFrozen()
}

@DangerousInternalIoApi
public actual fun Any.makeShared() {
    freeze()
}

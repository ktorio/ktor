/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import kotlin.native.concurrent.*

public actual fun Any.preventFreeze() {
    ensureNeverFrozen()
}

public actual fun Any.makeShared() {
    freeze()
}

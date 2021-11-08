/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import kotlin.native.concurrent.*

@OptIn(ExperimentalStdlibApi::class)
public actual fun Any.preventFreeze() {
    if (!isExperimentalMM()) {
        ensureNeverFrozen()
    }
}

@OptIn(ExperimentalStdlibApi::class)
public actual fun Any.makeShared() {
    if (!isExperimentalMM()) {
        freeze()
    }
}

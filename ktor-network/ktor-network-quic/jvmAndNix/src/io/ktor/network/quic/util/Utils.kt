/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.util

import kotlin.contracts.*

internal fun unreachable(): Nothing = error("unreachable")

@OptIn(ExperimentalContracts::class)
internal inline fun fastRepeat(times: Int, action: () -> Unit) {
    contract { callsInPlace(action) }

    var i = 0
    while (i < times) {
        action()
        i++
    }
}

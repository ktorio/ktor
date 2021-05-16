/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.features

import io.ktor.application.*

internal actual fun removeFromMDC(key: String) {
}

internal actual suspend inline fun withMDC(
    call: ApplicationCall,
    crossinline block: suspend () -> Unit
) {
    block()
}

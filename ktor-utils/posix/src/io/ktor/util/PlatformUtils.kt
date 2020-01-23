/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

@InternalAPI
actual object PlatformUtils {
    actual val IS_BROWSER: Boolean = false
    actual val IS_NODE: Boolean = false
    actual val IS_JVM: Boolean = false
    actual val IS_NATIVE: Boolean = true
}

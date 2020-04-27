/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

@InternalAPI
expect object PlatformUtils {
    val IS_BROWSER: Boolean
    val IS_NODE: Boolean
    val IS_JVM: Boolean
    val IS_NATIVE: Boolean
}

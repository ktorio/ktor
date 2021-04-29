/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

@InternalAPI
public actual object PlatformUtils {
    public actual val IS_BROWSER: Boolean = false
    public actual val IS_NODE: Boolean = false
    public actual val IS_JVM: Boolean = false
    public actual val IS_NATIVE: Boolean = true

    public actual val IS_DEVELOPMENT_MODE: Boolean = false
}

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

private const val DEVELOPMENT_MODE_KEY: String = "io.ktor.development"

public actual object PlatformUtils {
    public actual val IS_BROWSER: Boolean = false
    public actual val IS_NODE: Boolean = false
    public actual val IS_JVM: Boolean = true
    public actual val IS_NATIVE: Boolean = false

    public actual val IS_DEVELOPMENT_MODE: Boolean =
        System.getProperty(DEVELOPMENT_MODE_KEY)?.toBoolean() == true
}

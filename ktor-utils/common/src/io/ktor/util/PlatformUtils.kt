/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

@InternalAPI
public expect object PlatformUtils {
    public val IS_BROWSER: Boolean
    public val IS_NODE: Boolean
    public val IS_JVM: Boolean
    public val IS_NATIVE: Boolean

    public val IS_DEVELOPMENT_MODE: Boolean
}

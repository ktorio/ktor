// ktlint-disable filename
/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

public actual object PlatformUtils {
    public actual val IS_BROWSER: Boolean = js(
        "typeof window !== 'undefined' && typeof window.document !== 'undefined'"
    ) as Boolean

    public actual val IS_NODE: Boolean = js(
        "typeof process !== 'undefined' && process.versions != null && process.versions.node != null"
    ) as Boolean

    public actual val IS_JVM: Boolean = false
    public actual val IS_NATIVE: Boolean = false
    public actual val IS_DEVELOPMENT_MODE: Boolean = false
}

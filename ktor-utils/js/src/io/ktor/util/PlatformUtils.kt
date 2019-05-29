/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

object PlatformUtils {
    @KtorExperimentalAPI
    @Suppress("KDocMissingDocumentation")
    val IS_BROWSER: Boolean = js(
        "typeof window !== 'undefined' && typeof window.document !== 'undefined'"
    ) as Boolean

    @KtorExperimentalAPI
    @Suppress("KDocMissingDocumentation")
    val IS_NODE: Boolean = js(
        "typeof process !== 'undefined' && process.versions != null && process.versions.node != null"
    ) as Boolean
}


/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

/**
 * Determines whether a canonical domain is a public suffix.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.PublicSuffixRules)
 */
public fun interface PublicSuffixRules {
    /**
     * @return `true` when [canonicalDomain] is a public suffix.
     */
    public fun isPublicSuffix(canonicalDomain: String): Boolean

    public companion object
}

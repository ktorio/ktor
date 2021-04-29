/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.auth

/**
 * Describes how a header should be encoded.
 */
public enum class HeaderValueEncoding {
    /**
     * The header will be quoted only when required.
     */
    QUOTED_WHEN_REQUIRED,

    /**
     * The header will be quoted always.
     */
    QUOTED_ALWAYS,

    /**
     * The header will be URI-encoded as described in the RFC-3986:
     *
     * see https://tools.ietf.org/html/rfc3986#page-12
     */
    URI_ENCODE
}

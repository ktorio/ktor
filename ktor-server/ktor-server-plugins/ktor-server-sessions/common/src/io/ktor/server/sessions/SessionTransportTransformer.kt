/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

/**
 * A transformer used to sign and encrypt session data.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SessionTransportTransformer)
 */
public interface SessionTransportTransformer {
    /**
     * Untransforms a [transportValue] that represents a transformed session.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SessionTransportTransformer.transformRead)
     *
     * @return Untransformed value or null
     */
    public fun transformRead(transportValue: String): String?

    /**
     * Transforms a [transportValue] that represents session data.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SessionTransportTransformer.transformWrite)
     *
     * @return Transformed value
     */
    public fun transformWrite(transportValue: String): String
}

/**
 * Un-applies a list of session transformations to a [cookieValue] representing a transformed session string.
 * If any of the unapplication of transformations fail returning a null, this function also returns null.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.transformRead)
 *
 * @return A string representing the original session contents.
 */
public fun List<SessionTransportTransformer>.transformRead(cookieValue: String?): String? {
    val value = cookieValue ?: return null
    return this.asReversed().fold(value) { v, t -> t.transformRead(v) ?: return null }
}

/**
 * Applies a list of session transformations to a [value] representing session data.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.transformWrite)
 *
 * @return A string containing all the transformations applied.
 */
public fun List<SessionTransportTransformer>.transformWrite(value: String): String {
    return fold(value) { it, transformer -> transformer.transformWrite(it) }
}

/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.sessions

import io.ktor.http.*

private const val SameSiteKey: String = "SameSite"

/**
 * String constant options for SameSite cookie attribute.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SameSite)
 */
public object SameSite {
    /**
     * Only sends cookies from the origin's site.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SameSite.Strict)
     */
    public const val Strict: String = "Strict"

    /**
     * Default behavior. Also sends cookies when navigating to origin site.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SameSite.Lax)
     */
    public const val Lax: String = "Lax"

    /**
     * Also sends cookies from cross-site requests.
     * Requires Secure attribute to also be set.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SameSite.None)
     */
    public const val None: String = "None"
}

/**
 * Cookie configuration extension to supply the "SameSite" attribute for
 * preventing cross-site request forgery (CSRF) attacks.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.sameSite)
 */
public var CookieConfiguration.sameSite: String?
    get() = extensions[SameSiteKey]
    set(value) {
        extensions[SameSiteKey] = value
    }

/**
 * Extension for easier access of SameSite attribute.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.sameSite)
 */
public val Cookie.sameSite: String? get() =
    extensions[SameSiteKey]

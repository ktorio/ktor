/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.sessions.*
import io.ktor.utils.io.*
import kotlin.time.Duration

/**
 * Reads and writes the short-lived cookie that carries the in-flight state of an authentication protocol between
 * the redirect to the identity provider and the callback.
 */
@InternalAPI
public class AuthenticationStateCookie(
    public val name: String,
    public val maxAge: Duration,
    public val sameSite: String = SameSite.Lax,
) {

    init {
        require(maxAge.isPositive() && maxAge.inWholeSeconds <= Int.MAX_VALUE) {
            "State cookie '$name' max age must be positive and at most ${Int.MAX_VALUE} seconds, but was $maxAge"
        }
        require(sameSite in SameSiteValues) {
            "State cookie '$name' SameSite must be one of $SameSiteValues, but was '$sameSite'"
        }
    }

    /**
     * Sends the cookie with [value] in the [call] response.
     */
    public fun write(call: ApplicationCall, value: String) {
        call.response.cookies.append(cookie(call, value, maxAge.inWholeSeconds.toInt()))
    }

    public fun read(call: ApplicationCall): String? = call.request.cookies[name]

    /**
     * Instructs the browser to delete the cookie.
     */
    public fun clear(call: ApplicationCall) {
        call.response.cookies.append(cookie(call, value = "", maxAge = 0))
    }

    private fun cookie(call: ApplicationCall, value: String, maxAge: Int): Cookie = Cookie(
        name = name,
        value = value,
        maxAge = maxAge,
        path = "/",
        httpOnly = true,
        secure = sameSite == SameSite.None || call.request.origin.scheme == "https",
        extensions = mapOf("SameSite" to sameSite),
    )
}

private val SameSiteValues = listOf(SameSite.Strict, SameSite.Lax, SameSite.None)

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.auth

@Deprecated(
    message = "Moved to io.ktor.client.plugins.auth",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Auth", "io.ktor.client.plugins.auth.*")
)
public class Auth(
    public val providers: MutableList<AuthProvider> = mutableListOf()
)

@Deprecated(
    message = "Moved to io.ktor.client.plugins.auth",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Auth(block)", "io.ktor.client.plugins.auth.*")
)
public fun Auth(block: Auth.() -> Unit): Unit = error("Moved to io.ktor.client.features.auth")

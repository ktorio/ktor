/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.cookies

@Deprecated(
    message = "Moved to io.ktor.client.plugins.cookies",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("CookiesStorage", "io.ktor.client.plugins.cookies.*")
)
public interface CookiesStorage

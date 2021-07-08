/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpPlainText", "io.ktor.client.plugins.*")
)
public class HttpPlainText

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("Charsets(block)", "io.ktor.client.plugins.*")
)
public fun Charsets(block: () -> Unit): Unit =
    error("Moved to io.ktor.client.plugins")


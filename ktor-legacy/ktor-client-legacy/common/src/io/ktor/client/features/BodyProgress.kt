/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("BodyProgress", "io.ktor.client.plugins.*")
)
public class BodyProgress

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("onDownload(listener)", "io.ktor.client.plugins.*")
)
public fun onDownload(listener: Unit): Unit = error("Moved to io.ktor.client.plugins")

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("onUpload(listener)", "io.ktor.client.plugins.*")
)
public fun onUpload(listener: Unit): Unit = error("Moved to io.ktor.client.plugins")

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("HttpClientPlugin", "io.ktor.client.plugins.*")
)
public interface HttpClientPlugin<out TConfig : Any, TPlugin : Any>

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("plugin(plugin)", "io.ktor.client.plugins.*")
)
public fun <B : Any, F : Any> plugin(plugin: HttpClientPlugin<B, F>): F? =
    error("Moved to io.ktor.client.plugins")

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
public interface HttpClientFeature<out TConfig : Any, TFeature : Any>

@Deprecated(
    message = "Moved to io.ktor.client.plugins",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("plugin(plugin)", "io.ktor.client.plugins.*")
)
public fun <B : Any, F : Any> feature(feature: HttpClientFeature<B, F>): F? =
    error("Moved to io.ktor.client.plugins")

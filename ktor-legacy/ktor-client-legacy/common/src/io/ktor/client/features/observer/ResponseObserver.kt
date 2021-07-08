/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.observer

@Deprecated(
    message = "Moved to io.ktor.client.plugins.observer",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ResponseHandler", "io.ktor.client.plugins.observer.*")
)
public typealias ResponseHandler = suspend (Any) -> Unit

@Deprecated(
    message = "Moved to io.ktor.client.plugins.observer",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ResponseObserver(block)", "io.ktor.client.plugins.observer.*")
)
public fun ResponseObserver(block: ResponseHandler): Unit = error("Moved to io.ktor.client.plugins.observer")

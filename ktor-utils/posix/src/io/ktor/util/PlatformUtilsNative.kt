/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

internal actual val PlatformUtils.isDevelopmentMode: Boolean
    get() = false

@Deprecated(
    "New memory model is now enabled by default. The property will be removed in the future.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("true")
)
internal actual val PlatformUtils.isNewMemoryModel: Boolean
    get() = true

public actual val PlatformUtils.platform: Platform
    get() = Platform.Native

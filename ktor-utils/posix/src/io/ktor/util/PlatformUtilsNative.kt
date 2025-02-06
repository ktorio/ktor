/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

internal actual val PlatformUtils.isDevelopmentMode: Boolean
    get() = false

public actual val PlatformUtils.platform: Platform
    get() = Platform.Native

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

private const val DEVELOPMENT_MODE_KEY: String = "io.ktor.development"

public actual val PlatformUtils.platform: Platform
    get() = Platform.Jvm

internal actual val PlatformUtils.isDevelopmentMode: Boolean
    get() = System.getProperty(DEVELOPMENT_MODE_KEY)?.toBoolean() == true

internal actual val PlatformUtils.isNewMemoryModel: Boolean
    get() = true

public actual val PlatformUtils.threadId: String get() = Thread.currentThread().name

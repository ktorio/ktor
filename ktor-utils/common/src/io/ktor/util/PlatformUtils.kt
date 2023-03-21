/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

public object PlatformUtils {
    public val IS_BROWSER: Boolean = platform == Platform.Browser
    public val IS_NODE: Boolean = platform == Platform.Node
    public val IS_JVM: Boolean = platform == Platform.Jvm
    public val IS_NATIVE: Boolean = platform == Platform.Native

    public val IS_DEVELOPMENT_MODE: Boolean = isDevelopmentMode

    public val IS_NEW_MM_ENABLED: Boolean = isNewMemoryModel
}

internal expect val PlatformUtils.isDevelopmentMode: Boolean
internal expect val PlatformUtils.isNewMemoryModel: Boolean

public expect val PlatformUtils.platform: Platform

public enum class Platform {
    Jvm, Native, Browser, Node
}

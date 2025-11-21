/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import org.jetbrains.kotlin.konan.target.HostManager

val HostManager.Companion.hostTarget: String?
    get() {
        val prefix = when (hostOs()) {
            "linux" -> "linux"
            "osx" -> "macos"
            "windows" -> "mingw"
            else -> return null
        }
        val suffix = when (hostArch()) {
            "aarch64" -> "Arm64"
            "x86_64" -> "X64"
            else -> return null
        }
        return "$prefix$suffix"
    }

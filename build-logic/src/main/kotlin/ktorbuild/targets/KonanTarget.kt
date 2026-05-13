/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import org.jetbrains.kotlin.konan.target.KonanTarget

val KonanTarget.sourceSet: String
    get() = when (this) {
        KonanTarget.MACOS_ARM64 -> "macosArm64"
        KonanTarget.MACOS_X64 -> "macosX64"
        KonanTarget.LINUX_X64 -> "linuxX64"
        KonanTarget.LINUX_ARM64 -> "linuxArm64"
        KonanTarget.MINGW_X64 -> "mingwX64"
        else -> error("Unsupported target: $this")
    }

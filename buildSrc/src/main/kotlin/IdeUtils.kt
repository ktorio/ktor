/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.konan.target.*

fun KotlinMultiplatformExtension.createIdeaTarget(name: String): KotlinNativeTarget = when (HostManager.host) {
    is KonanTarget.LINUX_X64 -> linuxX64(name)
    is KonanTarget.MACOS_X64 -> macosX64(name)
    is KonanTarget.MACOS_ARM64 -> macosArm64(name)
    is KonanTarget.MINGW_X64 -> mingwX64(name)
    else -> error("Unsupported target ${HostManager.host}")
}

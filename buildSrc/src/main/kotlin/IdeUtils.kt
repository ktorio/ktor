/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.konan.target.*

fun KotlinMultiplatformExtension.ideaTarget(): KotlinNativeTarget = when (HostManager.host) {
    is KonanTarget.LINUX_X64 -> linuxX64()
    is KonanTarget.LINUX_ARM64 -> linuxArm64()
    is KonanTarget.MACOS_X64 -> macosX64()
    is KonanTarget.MACOS_ARM64 -> macosArm64()
    is KonanTarget.MINGW_X64 -> mingwX64()
    else -> error("Unsupported target ${HostManager.host}")
}

fun Project.fastTarget(): Boolean {
    if (COMMON_JVM_ONLY) kotlin.jvm()
    return COMMON_JVM_ONLY
}

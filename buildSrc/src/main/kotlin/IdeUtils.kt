/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.konan.target.*

fun KotlinMultiplatformExtension.createIdeaTarget(name: String): KotlinNativeTarget = when {
    HostManager.hostIsLinux -> linuxX64(name)
    HostManager.hostIsMac -> macosX64(name)
    HostManager.hostIsMingw -> mingwX64(name)
    else -> error("Unknown target")
}

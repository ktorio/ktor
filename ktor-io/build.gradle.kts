/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop
import ktorbuild.targets.sourceSet
import ktorbuild.vcpkg.*
import org.jetbrains.kotlin.konan.target.*

plugins {
    id("ktorbuild.project.library")
    alias(libs.plugins.kover)
}

val interopDir = file("linux/interop")
val includeDir = interopDir.resolve("include")
fun libraryPath(target: String) = interopDir.resolve("lib/$target")

kotlin {
    createCInterop("mutex", sourceSet = "posix")
    createCInterop("liburing", sourceSet = "linux") { target ->
        includeDirs(includeDir)
        extraOpts("-libraryPath", libraryPath(target))
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.io.core)
        }
        commonTest.dependencies {
            implementation(projects.ktorTestBase)
        }
    }
}

val target = providers.gradleProperty("vcpkg.target")
    .map { KonanTarget.predefinedTargets.getValue(it) }
    .orNull ?: HostManager.host

val liburingInstall = registerVcpkgInstallTask("liburing", target)

val liburingUpdateHeaders = registerSyncHeadersTask(
    "liburingUpdateHeaders",
    from = liburingInstall,
    into = includeDir,
    library = "liburing",
    topLevelHeaders = listOf("liburing.h"),
)

registerSyncBinariesTask("liburingUpdate", from = liburingInstall, into = libraryPath(target.sourceSet)) {
    dependsOn(liburingUpdateHeaders)
}

val ioUringJniTarget = providers.gradleProperty("vcpkg.target")
    .map { KonanTarget.predefinedTargets.getValue(it) }
    .orNull ?: KonanTarget.LINUX_X64

val ioUringJniArch = when (ioUringJniTarget) {
    KonanTarget.LINUX_X64 -> "x86_64"
    KonanTarget.LINUX_ARM64 -> "aarch64"
    else -> error("Unsupported io_uring JNI target: $ioUringJniTarget")
}

val ioUringJniInstall = registerVcpkgInstallTask(
    "ioUringJni",
    ioUringJniTarget,
    manifestDirectory = "jvm/native",
) {
    overlayPorts.add("ports/ktor-io-uring-jni")
}

registerSyncBinariesTask(
    "ioUringJniUpdate",
    from = ioUringJniInstall,
    into = file("jvm/resources/META-INF/native/linux-$ioUringJniArch"),
) {
    include("libktor_io_uring.so")
}

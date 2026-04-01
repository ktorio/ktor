/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop
import ktorbuild.targets.sourceSet
import ktorbuild.vcpkg.*
import org.jetbrains.kotlin.konan.target.*

plugins {
    id("ktorbuild.project.library")
    id("kotlinx-serialization")
    id("test-server")
}

val interopDir = file("desktop/interop")
val includeDir = interopDir.resolve("include")
fun libraryPath(target: String) = interopDir.resolve("lib/$target")

kotlin {
    createCInterop("libcurl", sourceSet = "desktop") { target ->
        includeDirs(includeDir)
        extraOpts("-libraryPath", libraryPath(target))
    }

    sourceSets {
        desktopMain.dependencies {
            api(projects.ktorClientCore)
            api(projects.ktorHttpCio)
        }
        desktopTest.dependencies {
            implementation(projects.ktorClientTests)
            implementation(projects.ktorClientLogging)
            implementation(projects.ktorClientJson)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

//region Libcurl tasks
// Register `libcurlUpdate` task updating libcurl binaries for the current host OS and architecture.
// This task should be run on each platform separately to update libcurl for all platforms.
// To build linuxArm64 binaries, the flag `-Pvcpkg.target=linux_arm64` should be passed.
val target = providers.gradleProperty("vcpkg.target")
    .map { KonanTarget.predefinedTargets.getValue(it) }
    .orNull ?: HostManager.host

val libcurlInstall = registerVcpkgInstallTask("libcurl", target) {
    // Link against system zlib (except for Windows)
    if (target != KonanTarget.MINGW_X64) overlayPorts.add("ports/zlib")
}

val libcurlUpdateHeaders = registerSyncHeadersTask(
    "libcurlUpdateHeaders",
    from = libcurlInstall,
    into = includeDir,
    library = "curl"
)

registerSyncBinariesTask("libcurlUpdate", from = libcurlInstall, into = libraryPath(target.sourceSet)) {
    dependsOn(libcurlUpdateHeaders)
}
//endregion

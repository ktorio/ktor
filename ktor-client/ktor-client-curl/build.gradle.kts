/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop
import ktorbuild.targets.KtorTargets
import ktorbuild.vcpkg.*

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
            api(projects.ktorClientLogging)
            api(projects.ktorClientJson)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

//region Libcurl tasks
// Register `libcurlUpdate[Target]` tasks updating libcurl binaries for the corresponding desktop target.
// These tasks should be run only in the suitable environment to avoid cross-compilation.
// To update libcurl binaries, run `libcurlUpdate*` on each platform.
for (target in KtorTargets.resolveTargets("desktop")) {
    val capitalizedTarget = target.replaceFirstChar { it.uppercase() }
    val libcurlInstall = tasks.register<VcpkgInstall>("libcurlInstall$capitalizedTarget") {
        install(manifestPath = "libcurl", target = target)
        // Use overlays to link against system zlib (except for Windows)
        useOverlays = target != "mingwX64"
    }

    // Headers are equal for all targets, so update them only once
    val libcurlUpdateHeaders = if (target == "macosArm64") {
        registerSyncHeadersTask("libcurlUpdateHeaders", from = libcurlInstall, into = includeDir, library = "curl")
    } else {
        null
    }

    registerSyncBinariesTask("libcurlUpdate$capitalizedTarget", from = libcurlInstall, into = libraryPath(target)) {
        if (libcurlUpdateHeaders != null) dependsOn(libcurlUpdateHeaders)
    }
}
//endregion

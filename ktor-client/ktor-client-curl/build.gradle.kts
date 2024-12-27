/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.createCInterop

val paths = listOf(
    "/opt/homebrew/opt/curl/include/",
    "/opt/local/include/",
    "/usr/local/include/",
    "/usr/include/",
    "/usr/local/opt/curl/include/",
    "/usr/include/x86_64-linux-gnu/",
    "/usr/local/Cellar/curl/7.62.0/include/",
    "/usr/local/Cellar/curl/7.63.0/include/",
    "/usr/local/Cellar/curl/7.65.3/include/",
    "/usr/local/Cellar/curl/7.66.0/include/",
    "/usr/local/Cellar/curl/7.80.0/include/",
    "/usr/local/Cellar/curl/7.80.0_1/include/",
    "/usr/local/Cellar/curl/7.81.0/include/",
    "desktop/interop/mingwX64/include/",
)

plugins {
    id("kotlinx-serialization")
    id("test-server")
}

kotlin {
    createCInterop(
        "libcurl",
        sourceSet = "desktop",
        definitionFilePath = { target ->
            when (target) {
                "macosArm64" -> "desktop/interop/libcurl_arm64.def"
                "linuxArm64" -> "desktop/interop/libcurl_linux_arm64.def"
                else -> "desktop/interop/libcurl.def"
            }
        },
        configure = { target ->
            val included = if (target == "linuxArm64") listOf("desktop/interop/linuxArm64/include/") else paths
            includeDirs.headerFilterOnly(included)
        }
    )

    sourceSets {
        desktopMain {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
            }
        }
        desktopTest {
            dependencies {
                api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
                api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            }
        }
    }
}

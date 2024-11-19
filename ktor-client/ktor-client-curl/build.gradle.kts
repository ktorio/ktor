/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.plugin.mpp.*

plugins {
    id("kotlinx-serialization")
    id("test-server")
}

kotlin {
    targets.withType<KotlinNativeTarget>().configureEach {
        compilations.named("main") {
            cinterops.create("libcurl") {
                definitionFile = file("desktop/interop/libcurl.def")
                includeDirs(file("desktop/interop/include"))
                extraOpts("-libraryPath", file("desktop/interop/lib/${target.name}"))
            }
        }
    }

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

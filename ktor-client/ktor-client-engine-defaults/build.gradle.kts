/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "A curated set of Ktor engines for providing multiplatform support"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorClientCore)
        }
        jvmMain.dependencies {
            api(projects.ktorClientOkhttp)
        }
        linuxMain.dependencies {
            api(projects.ktorClientCurl)
        }
        windowsMain.dependencies {
            api(projects.ktorClientWinhttp)
        }
        darwinMain.dependencies {
            api(projects.ktorClientDarwin)
        }
        androidNativeMain.dependencies {
            api(projects.ktorClientCio)
        }
    }
}

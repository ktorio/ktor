/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.UpdatePublicSuffixList

description = "Ktor http client"

plugins {
    id("ktorbuild.project.library")
}

tasks.register<UpdatePublicSuffixList>("updatePublicSuffixList") {
    val destinationFile = layout.projectDirectory.file("jvm/src/io/ktor/client/plugins/cookies/PublicSuffixListData.kt")

    group = "documentation"
    description = "Downloads and generates the bundled Public Suffix List"
    sourceUrl.set("https://publicsuffix.org/list/public_suffix_list.dat")
    destination.set(destinationFile)
    outputs.upToDateWhen { false }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ktorHttp)
            api(projects.ktorHttpCio)
            api(projects.ktorEvents)
            api(projects.ktorWebsocketSerialization)
            api(projects.ktorSse)
        }

        jvmMain.dependencies {
            implementation(libs.kotlinx.coroutines.slf4j)
        }

        jsMain.dependencies {
            api(npm("ws", libs.versions.ws.get()))
        }

        wasmJsMain.dependencies {
            api(npm("ws", libs.versions.ws.get()))
        }

        commonTest.dependencies {
            implementation(projects.ktorTestBase)
            implementation(projects.ktorClientMock)
            implementation(projects.ktorServerTestHost)
            implementation(projects.ktorClientContentNegotiation)
        }
    }
}

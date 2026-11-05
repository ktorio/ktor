/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.UpdatePublicSuffixList
import ktorbuild.targets.*

description = "Public Suffix List support for Ktor client cookies"

plugins {
    id("ktorbuild.optional.android-library")
    id("ktorbuild.project.library")
}

val publicSuffixAssets = layout.projectDirectory.dir("public-suffix-list/assets")
val publicSuffixResources = layout.projectDirectory.dir("public-suffix-list/resources")

tasks.register<UpdatePublicSuffixList>("updatePublicSuffixList") {
    group = "documentation"
    description = "Downloads and generates the bundled Public Suffix List"
    sourceUrl.set("https://publicsuffix.org/list/public_suffix_list.dat")
    binaryDestination.set(
        publicSuffixAssets.file("io/ktor/client/plugins/cookies/publicsuffix/PublicSuffixDatabase.list")
    )
    noticeDestination.set(
        publicSuffixAssets.file("io/ktor/client/plugins/cookies/publicsuffix/PUBLIC_SUFFIX_LIST_NOTICE.txt")
    )
    outputs.upToDateWhen { false }
}

kotlin {
    optionalAndroid {
        namespace = "io.ktor.client.cookies.publicsuffix"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.ktorClientCore)
        }

        jvmMain {
            resources.srcDir(publicSuffixAssets)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
        }

        optional.androidMain.configureEach {
            // The Android KMP plugin derives assets from the sibling directory of this resources directory.
            resources.srcDir(publicSuffixResources)
        }

        optional.androidHostTest.dependencies {
            implementation(libs.mockk)
            implementation(kotlin("test-junit"))
        }
    }
}

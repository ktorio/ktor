/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.targets

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import ktorbuild.internal.kotlin
import ktorbuild.internal.libs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke

internal fun Project.hasAndroidPlugin(): Boolean {
    return plugins.hasPlugin("com.android.kotlin.multiplatform.library")
}

@Suppress("UnstableApiUsage")
internal fun KotlinMultiplatformAndroidLibraryTarget.addTests(targets: KtorTargets, allowDeviceTest: Boolean) {
    if (targets.isEnabled("android.unitTest")) {
        withHostTest {}
    }
    if (allowDeviceTest && targets.isEnabled("android.deviceTest")) {
        withDeviceTestBuilder {
            // make it depend on a commonTest
            sourceSetTreeName = "test"
        }.configure {
            // androidx.test.runner should be installed
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
}

internal fun Project.configureAndroidJvm() {
    kotlin {
        sourceSets {
            named { it == "androidDeviceTest" }.configureEach {
                dependencies {
                    implementation(libs.androidx.core)
                    implementation(libs.androidx.runner)
                }
            }
        }
    }
}

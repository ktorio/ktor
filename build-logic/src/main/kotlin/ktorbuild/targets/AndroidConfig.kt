/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("UnstableApiUsage")

package ktorbuild.targets

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.androidLibrary
import dependencies
import ktorbuild.internal.kotlin
import ktorbuild.internal.libs
import optional
import org.gradle.api.Project
import org.gradle.kotlin.dsl.invoke
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private const val ANDROID_PLUGIN_ID = "com.android.kotlin.multiplatform.library"

fun KotlinMultiplatformExtension.optionalAndroidLibrary(action: KotlinMultiplatformAndroidLibraryTarget.() -> Unit) {
    project.pluginManager.withPlugin(ANDROID_PLUGIN_ID) {
        androidLibrary(action)
    }
}

internal fun Project.hasAndroidPlugin(): Boolean {
    return plugins.hasPlugin(ANDROID_PLUGIN_ID)
}

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
            optional.androidDeviceTest.dependencies {
                implementation(libs.androidx.core)
                implementation(libs.androidx.runner)
            }
        }
    }
}

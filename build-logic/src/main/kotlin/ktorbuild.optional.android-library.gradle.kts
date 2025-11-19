/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.*
import ktorbuild.internal.gradle.localProperty
import org.gradle.kotlin.dsl.support.serviceOf

if (isAndroidSdkAvailable()) {
    apply(plugin = "com.android.kotlin.multiplatform.library")
} else @Suppress("UnstableApiUsage") {
    val problemReporter = project.serviceOf<Problems>().reporter
    problemReporter.reportVisible(
        KtorBuildProblems.missingAndroidSdk,
        details = "Android SDK not found.",
        contextualLabel = "Android target won't be added to the project ${project.path}.",
    ) {
        solution("Download Android SDK from Android Studio or sdkmanager: https://developer.android.com/tools/sdkmanager")
        solution("Set ANDROID_HOME environment variable to your Android SDK path")
        solution("Create local.properties file with: sdk.dir=/path/to/your/android/sdk")
        buildFileLocation()
        documentedAt("https://github.com/ktorio/ktor/blob/main/CONTRIBUTING.md#building-the-project")
    }
}

private fun Project.isAndroidSdkAvailable(): Boolean =
    providers.environmentVariable("ANDROID_HOME")
        .orElse(localProperty("sdk.dir"))
        .isPresent

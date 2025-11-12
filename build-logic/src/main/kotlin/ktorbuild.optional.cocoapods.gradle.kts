/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.*
import ktorbuild.internal.gradle.localProperty
import ktorbuild.targets.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.konan.target.HostManager
import java.io.OutputStream

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    val cocoaPodsAvailable = localProperty(COCOAPODS_BIN_PROPERTY).map { true }
        .orElse(providers.gradleProperty(COCOAPODS_BIN_PROPERTY).map { true })
        .orElse(providers.of(CocoaPodsAvailableValueSource::class) {})

    if (!HostManager.hostIsMac || cocoaPodsAvailable.get()) {
        apply(plugin = COCOAPODS_PLUGIN_ID)
    } else @Suppress("UnstableApiUsage") {
        // Disable Darwin targets if CocoaPods is not available
        ktorBuild.targets["darwin"] = false

        val problemReporter = project.serviceOf<Problems>().reporter
        problemReporter.reportVisible(
            KtorBuildProblems.missingCocoaPods,
            details = "CocoaPods not found.",
            contextualLabel = "Apple targets won't be added in the project.",
        ) {
            solution("Install CocoaPods")
            solution("Ensure 'pod' command is available in PATH")
            buildFileLocation()
            documentedAt("https://github.com/ktorio/ktor/blob/main/CONTRIBUTING.md#building-the-project")
        }
    }
}

internal abstract class CocoaPodsAvailableValueSource @Inject constructor(
    val execOperations: ExecOperations,
) : ValueSource<Boolean, ValueSourceParameters.None> {

    override fun obtain(): Boolean {
        val result = execOperations.exec {
            commandLine("which", "pod")
            isIgnoreExitValue = true
            standardOutput = OutputStream.nullOutputStream()
            errorOutput = OutputStream.nullOutputStream()
        }

        return result.exitValue == 0
    }
}

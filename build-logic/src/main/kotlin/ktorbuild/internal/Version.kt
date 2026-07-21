/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal

import org.gradle.api.Project

/**
 * Resolves the version for the current project based on the defined properties.
 * Properties "releaseVersion" and "eapVersion" are passed on CI as build parameters:
 * ```
 * ./gradlew build -PreleaseVersion=3.0.0
 * ```
 */
internal fun Project.resolveVersion(): String {
    val projectVersion = providers.gradleProperty("version")
        .orElse(providers.fileContents(layout.settingsDirectory.file("VERSION")).asText.map { it.trim() })
    val releaseVersion = providers.gradleProperty("releaseVersion")
    val eapVersion = projectVersion.zip(providers.gradleProperty("eapVersion")) { projectVersion, eapVersion ->
        "${projectVersion.removeSuffix("-SNAPSHOT")}-eap-$eapVersion"
    }

    return releaseVersion
        .orElse(eapVersion)
        .orElse(projectVersion)
        .get()
}

private val stableVersionRegex = Regex("""^\d+\.\d+\.\d+$""")

/** Checks whether the given [version] stable or not. */
internal fun isStableVersion(version: String) = version matches stableVersionRegex

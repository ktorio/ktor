/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("UnstableApiUsage")

package ktorbuild.internal

import org.gradle.api.Project
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.ProblemSpec

context(project: Project)
internal fun ProblemReporter.reportVisible(
    problemId: ProblemId,
    details: String,
    contextualLabel: String,
    spec: ProblemSpec.() -> Unit
) {
    report(problemId) {
        details(details)
        contextualLabel(contextualLabel)
        spec()
    }

    // IDEA-352280: IDEA doesn't support Problems API yet, so additionally report a warning in the log
    project.logger.warn("w: '${project.path}': $details $contextualLabel\nSee problems report for details.")
}

context(project: Project)
internal fun ProblemSpec.buildFileLocation() {
    fileLocation(project.buildFile.absolutePath)
}

@Suppress("UnstableApiUsage")
internal object KtorBuildProblems {
    private val group = ProblemGroup.create("ktor", "Ktor")

    val extraSourceSet = ProblemId.create(
        "ktor-extra-source-sets",
        "Extra source sets detected",
        group,
    )
    val missingAndroidSdk = ProblemId.create(
        "ktor-missing-android-sdk",
        "Missing Android SDK",
        group,
    )
}

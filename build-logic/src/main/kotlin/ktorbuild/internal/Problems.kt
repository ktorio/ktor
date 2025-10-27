/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId

@Suppress("UnstableApiUsage")
internal object KtorBuildProblems {
    private val group = ProblemGroup.create("ktor", "Ktor")

    val extraSourceSet = ProblemId.create(
        "ktor-extra-source-sets",
        "Extra source sets detected",
        group,
    )
}

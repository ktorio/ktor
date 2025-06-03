/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Register an empty task that will trigger project evaluation if requested.
 * This task should be registered for every project to make [dependsOnSubprojects] work.
 */
internal fun Project.registerProjectEvaluationTask() {
    tasks.register(TASK_NAME)
}

/**
 * Ensures that all subprojects are evaluated before the task is executed.
 */
fun Task.dependsOnSubprojects() {
    dependsOn(project.subprojects.map { subproject -> "${subproject.path}:$TASK_NAME" })
}

private const val TASK_NAME = "evaluateProject"

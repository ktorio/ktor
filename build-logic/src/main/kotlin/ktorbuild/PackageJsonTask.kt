/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.npm.PublicPackageJsonTask

/**
 * Register an empty task that will trigger all [PublicPackageJsonTask] tasks in the project.
 * This task should be registered for every project to make [dependsOnPackageJsonAggregation] work.
 */
internal fun Project.registerPackageJsonAggregationTask() {
    tasks.register(TASK_NAME) {
        dependsOn(tasks.withType<PublicPackageJsonTask>())
    }
}

/**
 * Ensures that all [PublicPackageJsonTask] tasks are executed before the task is executed.
 */
fun Task.dependsOnPackageJsonAggregation() {
    dependsOn(project.subprojects.map { subproject -> "${subproject.path}:$TASK_NAME" })
}

private const val TASK_NAME = "packageJsonAggregation"

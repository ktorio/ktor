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
 * This task should be registered for every project to make [wirePackageJsonAggregationTasks] work.
 */
internal fun Project.registerPackageJsonAggregationTasks() {
    registerPackageJsonAggregationTask("js")
    registerPackageJsonAggregationTask("wasmJs")
}

private fun Project.registerPackageJsonAggregationTask(target: String) {
    tasks.register(aggregationTaskName(target)) {
        dependsOn(tasks.withType<PublicPackageJsonTask>().named { it.startsWith(target) })
    }
}

/**
 * Applies a workaround to make the ':packageJsonUmbrella' task compatible with configuration on demand.
 * This function should be called on the root project.
 * Issue: https://youtrack.jetbrains.com/issue/KT-55701
 */
fun Project.wirePackageJsonAggregationTasks() {
    tasks.named { it == "kotlinPackageJsonUmbrella" }
        .configureEach { dependsOnPackageJsonAggregation("js") }
    tasks.named { it == "kotlinWasmPackageJsonUmbrella" }
        .configureEach { dependsOnPackageJsonAggregation("wasmJs") }
}

/**
 * Ensures that all [PublicPackageJsonTask] tasks are executed before the task is executed.
 */
private fun Task.dependsOnPackageJsonAggregation(target: String) {
    dependsOn(project.subprojects.map { subproject -> "${subproject.path}:${aggregationTaskName(target)}" })
}

private fun aggregationTaskName(target: String) = "${target}PackageJsonAggregation"

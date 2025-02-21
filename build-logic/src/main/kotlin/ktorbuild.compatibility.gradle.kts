/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("binary-compatibility-validator")
}

apiValidation {
    val excludeList = setOf(
        "ktor",
        "ktor-client-test-base",
        "ktor-client-tests",
        "ktor-client-js",
        "ktor-client-content-negotiation-tests",
        "ktor-serialization-kotlinx-tests",
        "ktor-serialization-tests",
        "ktor-client",
        "ktor-client-plugins",
        "ktor-server-test-suites",
        "ktor-server-test-base",
        "ktor-test-base",
    )

    val projects = mutableSetOf<Project>()
    val queue = arrayDequeOf(
        project(":ktor-client"),
        project(":ktor-http"),
        project(":ktor-network"),
        project(":ktor-utils"),
        project(":ktor-io"),
        project(":ktor-server"),
        project(":ktor-server:ktor-server-plugins"),
        project(":ktor-shared"),
    )

    while (queue.isNotEmpty()) {
        val currentProject = queue.removeLast()
        if (projects.add(currentProject)) {
            queue.addAll(currentProject.childProjects.values)
        }
    }

    val projectNames = projects.map { it.name }.toSet()

    ignoredProjects.addAll(excludeList)
    ignoredProjects.addAll(
        project.allprojects.map { it.name }.filter { !projectNames.contains(it) }
    )

    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}

fun <T> arrayDequeOf(vararg values: T): ArrayDeque<T> = ArrayDeque(values.asList())

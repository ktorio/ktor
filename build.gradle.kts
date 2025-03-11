/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

val internalProjects = listOf(
    "ktor-client-content-negotiation-tests",
    "ktor-client-test-base",
    "ktor-client-tests",
    "ktor-serialization-kotlinx-tests",
    "ktor-serialization-tests",
    "ktor-server-test-base",
    "ktor-server-test-suites",
    "ktor-server-tests",
    "ktor-test-base",
)

val nonDefaultProjectStructure by extra {
    listOf(
        "ktor-dokka",
        "ktor-bom",
        "ktor-java-modules-test",
    )
}

plugins {
    id("ktorbuild.doctor")
    id("ktorbuild.compatibility")
    id("ktorbuild.publish.verifier")
}

println("Build version: ${project.version}")

subprojects {
    when (project.name) {
        in nonDefaultProjectStructure -> apply(plugin = "ktorbuild.base")
        in internalProjects -> apply(plugin = "ktorbuild.project.internal")
        else -> apply(plugin = "ktorbuild.project.library")
    }
}

println("Using Kotlin compiler version: ${libs.versions.kotlin.get()}")

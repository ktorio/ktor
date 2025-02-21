/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

extra["globalM2"] = "${project.file("build")}/m2"
extra["publishLocal"] = project.hasProperty("publishLocal")

apply(from = "gradle/verifier.gradle")

val internalProjects = listOf(
    "ktor-client-test-base",
    "ktor-client-tests",
    "ktor-server-test-base",
    "ktor-server-test-suites",
    "ktor-server-tests",
    "ktor-client-content-negotiation-tests",
    "ktor-test-base",
)

// Point old artifact to new location
extra["relocatedArtifacts"] = mapOf(
    "ktor-server-test-base" to "ktor-server-test-host",
)

val nonDefaultProjectStructure by extra {
    listOf(
        "ktor-bom",
        "ktor-java-modules-test",
    )
}

plugins {
    id("ktorbuild.doctor")
    id("ktorbuild.compatibility")
    id("ktorbuild.dokka")
}

println("Build version: ${project.version}")

subprojects {
    when (project.name) {
        in nonDefaultProjectStructure -> apply(plugin = "ktorbuild.base")
        in internalProjects -> apply(plugin = "ktorbuild.project.internal")

        else -> {
            apply(plugin = "ktorbuild.project.library")
            configurePublication()
        }
    }
}

println("Using Kotlin compiler version: ${libs.versions.kotlin.get()}")

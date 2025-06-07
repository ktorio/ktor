/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.dependsOnPackageJsonAggregation

plugins {
    id("ktorbuild.doctor")
    id("ktorbuild.publish.verifier")
}

println("Build version: ${project.version}")
println("Kotlin version: ${libs.versions.kotlin.get()}")

// Workaround to make the ':packageJsonUmbrella' task compatible with configuration on demand
// Issue: https://youtrack.jetbrains.com/issue/KT-55701
tasks.named { it == "packageJsonUmbrella" }
    .configureEach { dependsOnPackageJsonAggregation() }

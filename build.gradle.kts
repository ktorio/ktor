/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.doctor")
    id("ktorbuild.compatibility")
    id("ktorbuild.publish.verifier")
}

println("Build version: ${project.version}")
println("Kotlin version: ${libs.versions.kotlin.get()}")

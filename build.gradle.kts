/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.root")
}

logger.lifecycle("Build version: ${project.version}")
logger.lifecycle("Kotlin version: ${libs.versions.kotlin.get()}")

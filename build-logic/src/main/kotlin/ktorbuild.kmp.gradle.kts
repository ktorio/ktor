/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.ktorBuild
import ktorbuild.targets.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("ktorbuild.base")
    kotlin("multiplatform")
}

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KtorTargets.hierarchyTemplate)
    addTargets(ktorBuild.targets)
}

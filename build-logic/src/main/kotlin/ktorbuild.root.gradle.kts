/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*
import ktorbuild.targets.*

plugins {
    id("ktorbuild.base")
    id("ktorbuild.doctor")
    id("ktorbuild.publish.verifier")
}

printSyncModeNotice()

wirePackageJsonAggregationTasks()

tasks.register("annotateKDocs") {
    group = "documentation"
    description = "Adds feedback links to public API KDocs."
    dependsOn(gradle.includedBuild("build-logic").task(":kdoc-annotator:run"))
}

configureYarn()
configureNodeJs()

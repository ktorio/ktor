/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*

plugins {
    id("ktorbuild.base")
    id("java-platform")
    id("ktorbuild.publish")
}

val allPublications = projectsWithTag(ProjectTag.Library)
    .flatMapValue { libraryProject ->
        libraryProject.publishing.publications
            .filterIsInstance<MavenPublication>()
            .filterNot { it.artifactId.endsWith("-metadata") || it.artifactId.endsWith("-kotlinMultiplatform") }
            .map { libraryProject.dependencies.constraints.create("${it.groupId}:${it.artifactId}:${it.version}") }
    }

configurations.api {
    dependencyConstraints.addAllLater(allPublications)
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*
import ktorbuild.internal.publish.ValidatePublishedArtifactsTask

val publishedProjects = projectsWithTag(ProjectTag.Published)

tasks.register<ValidatePublishedArtifactsTask>(ValidatePublishedArtifactsTask.NAME) {
    dependsOn(publishedProjects)
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*
import ktorbuild.internal.publish.*
import ktorbuild.internal.publish.TestRepository.configureTestRepository
import ktorbuild.internal.publish.TestRepository.locateTestRepository

val cleanTestRepository by tasks.registering(Delete::class) {
    delete(locateTestRepository())
}

val publishedProjects = projectsWithTag(ProjectTag.Published)

tasks.register<ValidatePublishedArtifactsTask>(ValidatePublishedArtifactsTask.NAME) {
    dependsOn(cleanTestRepository)

    publishedProjects.get().forEach { project ->
        with(project) {
            configureTestRepository()
            val publishTasks = tasks.withType<PublishToMavenRepository>()
                .matching { it.repository.name == TestRepository.NAME }
            publishTasks.configureEach { mustRunAfter(cleanTestRepository) }
            dependsOn(publishTasks)
        }
    }
}

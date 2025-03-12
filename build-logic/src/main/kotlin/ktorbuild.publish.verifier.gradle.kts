/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.publish.*
import ktorbuild.internal.publish.TestRepository.configureTestRepository
import ktorbuild.internal.publish.TestRepository.locateTestRepository

val cleanTestRepository by tasks.registering(Delete::class) {
    delete(locateTestRepository())
}

tasks.register<ValidatePublishedArtifactsTask>(ValidatePublishedArtifactsTask.NAME) {
    dependsOn(cleanTestRepository)

    rootProject.subprojects.forEach { subproject ->
        subproject.plugins.withId("maven-publish") {
            subproject.configureTestRepository()
            val publishTasks = subproject.tasks.withType<PublishToMavenRepository>()
                .matching { it.repository.name == TestRepository.NAME }
            publishTasks.configureEach { mustRunAfter(cleanTestRepository) }
            dependsOn(publishTasks)
        }
    }
}

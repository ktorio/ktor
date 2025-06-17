/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package ktorbuild.internal.publish

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.maven

internal fun RepositoryHandler.addTargetRepositoryIfConfigured() {
    val repositoryId = System.getenv("REPOSITORY_ID").orEmpty()
    val publishingUrl = if (repositoryId.isNotBlank()) {
        println("Set publishing to repository $repositoryId")
        "https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$repositoryId"
    } else {
        System.getenv("PUBLISHING_URL")
    }
    if (publishingUrl == null) return

    maven(url = publishingUrl) {
        name = System.getenv("REPOSITORY_NAME") ?: "maven"
        credentials {
            username = System.getenv("PUBLISHING_USER")
            password = System.getenv("PUBLISHING_PASSWORD")
        }
    }
}

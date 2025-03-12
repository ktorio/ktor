/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.base")
    id("java-platform")
    id("maven-publish")
}

publishing.publications {
    create<MavenPublication>("maven") {
        from(components.findByName("javaPlatform"))
    }
}

val name = project.name

dependencies {
    constraints {
        rootProject.subprojects.forEach subprojects@{
            if (!it.plugins.hasPlugin("maven-publish") || it.name == name) return@subprojects
            it.publishing.publications.forEach { publication ->
                if (publication !is MavenPublication) return@forEach

                val artifactId = publication.artifactId
                if (artifactId.endsWith("-metadata") || artifactId.endsWith("-kotlinMultiplatform")) {
                    return@forEach
                }

                api("${publication.groupId}:${publication.artifactId}:${publication.version}")
            }
        }
    }
}

apply(plugin = "ktorbuild.publish")

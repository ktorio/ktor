/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.*
import ktorbuild.internal.*
import ktorbuild.internal.gradle.findByName
import ktorbuild.internal.publish.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    id("maven-publish")
    id("signing") apply false
}

addProjectTag(ProjectTag.Published)

publishing {
    publications.configureEach {
        if (this !is MavenPublication) return@configureEach

        pom {
            name = project.name
            description = project.description.orEmpty()
                .ifEmpty { "Ktor is a framework for quickly creating web applications in Kotlin with minimal effort." }
            url = "https://github.com/ktorio/ktor"
            licenses {
                license {
                    name = "The Apache Software License, Version 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    distribution = "repo"
                }
            }
            developers {
                developer {
                    id = "JetBrains"
                    name = "Jetbrains Team"
                    organization = "JetBrains"
                    organizationUrl = "https://www.jetbrains.com"
                }
            }
            scm {
                url = "https://github.com/ktorio/ktor.git"
            }
        }
    }

    repositories {
        addTargetRepositoryIfConfigured()
        mavenLocal()
    }
}

registerCommonPublishTask()
configureSigning()

plugins.withId("ktorbuild.kmp") {
    tasks.withType<AbstractPublishToMaven>().configureEach {
        val os = ktorBuild.os.get()
        // Workaround for https://github.com/gradle/gradle/issues/22641
        val predicate = provider { isAvailableForPublication(publication.name, os) }
        onlyIf("Available for publication on $os") { predicate.get() }
    }

    registerTargetsPublishTasks(ktorBuild.targets)
    configureJavadocArtifact()
}

private fun Project.configureSigning() {
    extra["signing.gnupg.keyName"] = (System.getenv("SIGN_KEY_ID") ?: return)
    extra["signing.gnupg.passphrase"] = (System.getenv("SIGN_KEY_PASSPHRASE") ?: return)

    apply(plugin = "signing")

    signing {
        useGpgCmd()
        sign(publishing.publications)
    }

    // Workaround for https://github.com/gradle/gradle/issues/12167
    tasks.withType<Sign>().configureEach {
        withLimitedParallelism("gpg-agent", maxParallelTasks = 1)
    }
}

private fun Project.configureJavadocArtifact() {
    val emptyJar = tasks.register<Jar>("emptyJar") {
        archiveAppendix = "empty"
    }

    publishing {
        for (target in the<KotlinMultiplatformExtension>().targets) {
            val publication = publications.findByName<MavenPublication>(target.name) ?: continue

            publication.artifact(emptyJar) { classifier = "javadoc" }
            if (target.platformType.name != "jvm") {
                publication.artifact(emptyJar) { classifier = "kdoc" }
            }

            if (target.platformType.name == "native") {
                publication.artifact(emptyJar)
            }
        }
    }

    // We share emptyJar artifact between all publications, so all publish tasks should be run after all sign tasks.
    // Otherwise, Gradle will throw an error like:
    //   Task ':publishX' uses output of task ':signY' without declaring an explicit or implicit dependency.
    tasks.withType<AbstractPublishToMaven>().configureEach { mustRunAfter(tasks.withType<Sign>()) }
}

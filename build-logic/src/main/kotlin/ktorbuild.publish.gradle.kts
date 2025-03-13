/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import ktorbuild.internal.gradle.findByName
import ktorbuild.internal.ktorBuild
import ktorbuild.internal.publish.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.concurrent.locks.ReentrantLock

plugins {
    id("maven-publish")
    id("signing") apply false
}

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
        onlyIf { isAvailableForPublication(ktorBuild.os.get()) }
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

    val gpgAgentLock: ReentrantLock by rootProject.extra { ReentrantLock() }

    tasks.withType<Sign>().configureEach {
        doFirst { gpgAgentLock.lock() }
        doLast { gpgAgentLock.unlock() }
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

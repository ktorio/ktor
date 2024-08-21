/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.gradle.api.*
import org.gradle.api.publish.*
import org.gradle.api.publish.maven.*
import org.gradle.api.publish.maven.tasks.*
import org.gradle.jvm.tasks.*
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.*
import java.util.concurrent.locks.*

fun isAvailableForPublication(publication: Publication): Boolean {
    val name = publication.name
    if (name == "maven") return true

    var result = false
    val jvmAndCommon = setOf(
        "jvm",
        "androidRelease",
        "androidDebug",
        "js",
        "wasmJs",
        "metadata",
        "kotlinMultiplatform"
    )
    result = result || name in jvmAndCommon
    result = result || (HOST_NAME == "linux" && (name == "linuxX64" || name == "linuxArm64"))
    result = result || (HOST_NAME == "windows" && name == "mingwX64")
    val macPublications = setOf(
        "iosX64",
        "iosArm64",
        "iosSimulatorArm64",

        "watchosX64",
        "watchosArm32",
        "watchosArm64",
        "watchosSimulatorArm64",
        "watchosDeviceArm64",

        "tvosX64",
        "tvosArm64",
        "tvosSimulatorArm64",

        "macosX64",
        "macosArm64"
    )

    result = result || (HOST_NAME == "macos" && name in macPublications)

    // can be published from any host
    val androidNativePublication = setOf(
        "androidNativeArm32",
        "androidNativeArm64",
        "androidNativeX64",
        "androidNativeX86"
    )

    result = result || name in androidNativePublication

    return result
}

fun Project.configurePublication() {
    apply(plugin = "maven-publish")

    tasks.withType<AbstractPublishToMaven>().configureEach {
        onlyIf { isAvailableForPublication(publication) }
    }

    val publishingUser: String? = System.getenv("PUBLISHING_USER")
    val publishingPassword: String? = System.getenv("PUBLISHING_PASSWORD")

    val repositoryId: String? = System.getenv("REPOSITORY_ID")
    val publishingUrl: String? = if (repositoryId?.isNotBlank() == true) {
        println("Set publishing to repository $repositoryId")
        "https://oss.sonatype.org/service/local/staging/deployByRepositoryId/$repositoryId"
    } else {
        System.getenv("PUBLISHING_URL")
    }

    val publishLocal: Boolean by rootProject.extra
    val globalM2: String by rootProject.extra
    val nonDefaultProjectStructure: List<String> by rootProject.extra
    val relocatedArtifacts: Map<String, String> by rootProject.extra

    val emptyJar = tasks.register<Jar>("emptyJar") {
        archiveAppendix = "empty"
    }

    the<PublishingExtension>().apply {
        repositories {
            maven {
                if (publishLocal) {
                    setUrl(globalM2)
                } else {
                    publishingUrl?.let { setUrl(it) }
                    credentials {
                        username = publishingUser
                        password = publishingPassword
                    }
                }
            }
            maven {
                name = "testLocal"
                setUrl("$rootProject.buildDir/m2")
            }
        }

        publications.forEach {
            val publication = it as? MavenPublication ?: return@forEach

            publication.pom {
                name = project.name
                description = project.description?.takeIf { it.isNotEmpty() } ?: "Ktor is a framework for quickly creating web applications in Kotlin with minimal effort."
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
                relocatedArtifacts[project.name]?.let { newArtifactId ->
                    distributionManagement {
                        relocation {
                            artifactId = newArtifactId
                        }
                    }
                }
            }
        }

        if (nonDefaultProjectStructure.contains(project.name)) return@apply

        kotlin.targets.forEach { target ->
            val publication = publications.findByName(target.name) as? MavenPublication ?: return@forEach

            if (target.platformType.name == "jvm") {
                publication.artifact(emptyJar) {
                    classifier = "javadoc"
                }
            } else {
                publication.artifact(emptyJar) {
                    classifier = "javadoc"
                }
                publication.artifact(emptyJar) {
                    classifier = "kdoc"
                }
            }

            if (target.platformType.name == "native") {
                publication.artifact(emptyJar)
            }
        }
    }

    tasks.named("publish") {
        dependsOn(tasks.named("publishToMavenLocal"))
    }

    val signingKey = System.getenv("SIGN_KEY_ID")
    val signingKeyPassphrase = System.getenv("SIGN_KEY_PASSPHRASE")

    if (signingKey != null && signingKey != "") {
        extra["signing.gnupg.keyName"] = signingKey
        extra["signing.gnupg.passphrase"] = signingKeyPassphrase

        apply(plugin = "signing")

        the<SigningExtension>().apply {
            useGpgCmd()

            sign(the<PublishingExtension>().publications)
        }

        val gpgAgentLock: ReentrantLock by rootProject.extra { ReentrantLock() }

        tasks.withType<Sign> {
            doFirst {
                gpgAgentLock.lock()
            }

            doLast {
                gpgAgentLock.unlock()
            }
        }
    }

    val signLinuxArm64Publication = tasks.maybeNamed("signLinuxArm64Publication")
    if (signLinuxArm64Publication != null) {
        tasks.maybeNamed("publishLinuxX64PublicationToMavenRepository") {
            dependsOn(signLinuxArm64Publication)
        }
    }

    val signLinuxX64Publication = tasks.maybeNamed("signLinuxX64Publication")
    if (signLinuxX64Publication != null) {
        tasks.maybeNamed("publishLinuxArm64PublicationToMavenRepository") {
            dependsOn(signLinuxX64Publication)
        }
    }
}

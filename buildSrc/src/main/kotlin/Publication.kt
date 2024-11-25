/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import internal.*
import org.gradle.api.*
import org.gradle.api.publish.*
import org.gradle.api.publish.maven.*
import org.gradle.api.publish.maven.tasks.*
import org.gradle.api.publish.plugins.*
import org.gradle.jvm.tasks.*
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.*
import java.util.concurrent.locks.*

private val jvmAndCommonTargets = setOf(
    "jvm",
    "androidRelease",
    "androidDebug",
    "metadata",
    "kotlinMultiplatform",
    "maven",
)

private val jsTargets = setOf(
    "js",
    "wasmJs",
)

private val linuxTargets = setOf(
    "linuxX64",
    "linuxArm64",
)

private val windowsTargets = setOf(
    "mingwX64",
)

private val darwinTargets = setOf(
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
    "macosArm64",
)

private val androidNativeTargets = setOf(
    "androidNativeArm32",
    "androidNativeArm64",
    "androidNativeX64",
    "androidNativeX86",
)

fun Project.configurePublication() {
    apply(plugin = "maven-publish")

    tasks.withType<AbstractPublishToMaven>().configureEach {
        onlyIf { publication.isAvailableForPublication() }
    }
    configureAggregatingTasks()

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
    val relocatedArtifacts: Map<String, String> by rootProject.extra

    publishing {
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
                setUrl(rootProject.layout.buildDirectory.dir("m2"))
            }
        }

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
                relocatedArtifacts[project.name]?.let { newArtifactId ->
                    distributionManagement {
                        relocation {
                            artifactId = newArtifactId
                        }
                    }
                }
            }
        }
    }

    tasks.named("publish") {
        dependsOn(tasks.named("publishToMavenLocal"))
    }

    configureSigning()
    configureJavadocArtifact()
}

private fun Publication.isAvailableForPublication(): Boolean {
    val name = name

    var result = name in jvmAndCommonTargets || name in jsTargets || name in androidNativeTargets
    result = result || (HOST_NAME == "linux" && name in linuxTargets)
    result = result || (HOST_NAME == "windows" && name in windowsTargets)
    result = result || (HOST_NAME == "macos" && name in darwinTargets)

    return result
}

private fun Project.configureAggregatingTasks() {
    registerAggregatingTask("JvmAndCommon", jvmAndCommonTargets)
    if (hasJs || hasWasmJs) registerAggregatingTask("Js", jsTargets)
    if (hasLinux) registerAggregatingTask("Linux", linuxTargets)
    if (hasWindows) registerAggregatingTask("Windows", windowsTargets)
    if (hasDarwin) registerAggregatingTask("Darwin", darwinTargets)
    if (hasAndroidNative) registerAggregatingTask("AndroidNative", androidNativeTargets)
}

private fun Project.registerAggregatingTask(name: String, targets: Set<String>) {
    tasks.register("publish${name}Publications") {
        group = PublishingPlugin.PUBLISH_TASK_GROUP
        val targetsTasks = targets.mapNotNull { target ->
            tasks.maybeNamed("publish${target.capitalized()}PublicationToMavenRepository")
        }
        dependsOn(targetsTasks)
    }
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
    val nonDefaultProjectStructure: List<String> by rootProject.extra
    if (project.name in nonDefaultProjectStructure) return

    val emptyJar = tasks.register<Jar>("emptyJar") {
        archiveAppendix = "empty"
    }

    publishing {
        for (target in kotlin.targets) {
            val publication = publications.findByName<MavenPublication>(target.name) ?: continue

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

    // We share emptyJar artifact between all publications, so all publish tasks should be run after all sign tasks.
    // Otherwise Gradle will throw an error like:
    //   Task ':publishX' uses output of task ':signY' without declaring an explicit or implicit dependency.
    tasks.withType<AbstractPublishToMaven>().configureEach { mustRunAfter(tasks.withType<Sign>()) }
}

// Extension accessors
private val Project.publishing: PublishingExtension get() = extensions.getByName<PublishingExtension>("publishing")
private fun Project.publishing(block: PublishingExtension.() -> Unit) = extensions.configure("publishing", block)
private fun Project.signing(configure: SigningExtension.() -> Unit) = extensions.configure("signing", configure)

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

pluginManagement {
    /*
     * This property group is used to build Ktor against Kotlin compiler snapshot in two different configurations:
     *
     * Ktor K2:
     *   - kotlin_version overrides the Kotlin version to be used for project compilation
     *   - kotlin_repo_url defines additional repository to be added to the repository list
     *   - kotlin_language_version overrides Kotlin language versions
     *   - kotlin_api_version overrides Kotlin API version
     *
     * Ktor Train:
     *   All the above properties are also applied, and:
     *   - build_snapshot_train is set to true
     *   - atomicfu_version, coroutines_version and serialization_version are defined in TeamCity environment
     *   - Additionally Sonatype snapshots repository added to the repository list,
     *     and stress tests disabled.
     *
     * DO NOT change the names of these properties without adapting kotlinx.train build chain.
     */
    val buildSnapshotTrain by settings.extra {
        settings.providers.gradleProperty("build_snapshot_train").orNull.toBoolean()
    }
    // Share repositories configuration between pluginManagement and dependencyResolutionManagement blocks
    val configureRepositories by settings.extra {
        val additionalKotlinRepo = settings.providers.gradleProperty("kotlin_repo_url").orNull

        fun RepositoryHandler.() {
            google {
                content {
                    includeGroupAndSubgroups("com.google")
                    includeGroupAndSubgroups("com.android")
                }
            }
            mavenCentral()

            if (additionalKotlinRepo != null) {
                maven(additionalKotlinRepo) { name = "KotlinDevRepo" }
                logger.info("Kotlin Dev repository: $additionalKotlinRepo")
            }
            if (buildSnapshotTrain) {
                maven("https://oss.sonatype.org/content/repositories/snapshots") {
                    name = "SonatypeSnapshots"
                    mavenContent { snapshotsOnly() }
                }
            }

            mavenLocal()
        }
    }

    repositories {
        gradlePluginPortal()
        configureRepositories()
    }
}

val buildSnapshotTrain: Boolean by settings.extra
val configureRepositories: RepositoryHandler.() -> Unit by settings.extra
val kotlinVersion = settings.providers.gradleProperty("kotlin_version")
    // kotlin_version_snapshot might be used instead of kotlin_version
    .orElse(settings.providers.gradleProperty("kotlin_version_snapshot"))
    .orNull

dependencyResolutionManagement {
    repositories {
        configureRepositories()
    }

    versionCatalogs {
        create("libs") {
            if (file("../gradle/libs.versions.toml").exists()) {
                from(files("../gradle/libs.versions.toml"))
            }

            if (buildSnapshotTrain) {
                requireNotNull(kotlinVersion) {
                    "kotlin_version should be specified when building with build_snapshot_train=true"
                }
                version("kotlin", kotlinVersion)
                version("atomicfu", settings.extra["atomicfu_version"].toString())
                version("coroutines", settings.extra["coroutines_version"].toString())
                version("serialization", settings.extra["serialization_version"].toString())
            } else if (kotlinVersion != null) {
                version("kotlin", kotlinVersion)
            }
        }
    }
}

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

/*
 * This property group is used to build Ktor against Kotlin compiler snapshot.
 * How does it work:
 * When build_snapshot_train is set to true, kotlin version is overridden with kotlin_snapshot_version,
 * atomicfu_version, coroutines_version, serialization_version and kotlinx_io_version are overwritten by TeamCity environment.
 * Additionally, mavenLocal and Sonatype snapshots are added to repository list and stress tests are disabled.
 * DO NOT change the name of these properties without adapting kotlinx.train build chain.
 */
val buildSnapshotTrain =
    settings.extra.has("build_snapshot_train") && settings.extra["build_snapshot_train"] == "true"
val additionalKotlinRepo =
    if (settings.extra.has("kotlin_repo_url")) settings.extra["kotlin_repo_url"].toString() else null

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")

        additionalKotlinRepo?.let { maven(it) }
        if (buildSnapshotTrain) {
            maven("https://oss.sonatype.org/content/repositories/snapshots") {
                mavenContent { snapshotsOnly() }
            }
        }

        mavenLocal()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))

            if (buildSnapshotTrain) {
                version("kotlin", settings.extra["kotlin_snapshot_version"].toString())
                version("atomicfu", settings.extra["atomicfu_version"].toString())
                version("coroutines", settings.extra["coroutines_version"].toString())
                version("serialization", settings.extra["serialization_version"].toString())
            }
        }
    }
}

rootProject.name = "buildSrc"

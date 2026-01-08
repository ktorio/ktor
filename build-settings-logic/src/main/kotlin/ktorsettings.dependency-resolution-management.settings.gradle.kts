/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        configureRepositories()
    }
}

dependencyResolutionManagement {
    repositories {
        configureRepositories()
    }

    versionCatalogs {
        create("libs") {
            if (!file("gradle/libs.versions.toml").exists() && file("../gradle/libs.versions.toml").exists()) {
                from(files("../gradle/libs.versions.toml"))
            }

            downgradeTestDependencies()
        }

        create("kotlinWrappers") {
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:2025.12.12")
        }
    }
}

private fun RepositoryHandler.configureRepositories() {
    google {
        content {
            includeGroupAndSubgroups("androidx")
            includeGroupAndSubgroups("com.google")
            includeGroupAndSubgroups("com.android")
        }
    }
    mavenCentral()
    mavenLocal()

    exclusiveContent {
        forRepository {
            maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap") { name = "KtorEAP" }
        }
        filter { includeVersionByRegex("io.ktor", ".+", ".+-eap-\\d+") }
    }
}

private fun VersionCatalogBuilder.downgradeTestDependencies() {
    val testJdk = providers.gradleProperty("test.jdk").orNull?.toInt() ?: return

    if (testJdk < 11) version("logback", "1.3.14") // Java 8 support has been dropped in Logback 1.4.x
}

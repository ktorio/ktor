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
            from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:2026.1.9")
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

    // Shibboleth Nexus repository for OpenSAML 5.x artifacts
    maven("https://build.shibboleth.net/maven/releases/") {
        name = "Shibboleth"
        content {
            includeGroup("org.opensaml")
            includeGroup("net.shibboleth")
            includeGroup("net.shibboleth.utilities")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://packages.jetbrains.team/maven/p/ktor/eap") { name = "KtorEAP" }
        }
        filter { includeVersionByRegex("io.ktor", ".+", ".+-eap-\\d+") }
    }
}

private fun VersionCatalogBuilder.downgradeTestDependencies() {
    val testJdk = providers.gradleProperty("test.jdk").orNull?.toInt() ?: return

    if (testJdk < 11) version("logback", "1.3.14") // Java 8 support has been dropped in Logback 1.4.x
}

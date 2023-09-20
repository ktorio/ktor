/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

plugins {
    id("org.gradle.kotlin.kotlin-dsl") version "3.2.4"
    kotlin("plugin.serialization") version "1.7.20"
}

val buildSnapshotTrain = properties["build_snapshot_train"]?.toString()?.toBoolean() == true

extra["kotlin_repo_url"] = rootProject.properties["kotlin_repo_url"]
val kotlin_repo_url: String? by extra

repositories {
    mavenCentral()
    maven("https://plugins.gradle.org/m2")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    maven("https://cache-redirector.jetbrains.com/plugins.gradle.org/m2")
    maven("https://plugins.gradle.org/m2")
    mavenLocal()
    if (buildSnapshotTrain) {
        mavenLocal()
    }
    if (kotlin_repo_url != null) {
        maven(kotlin_repo_url!!)
    }
}

sourceSets.main {
}

configurations.configureEach {
    if (isCanBeResolved) {
        attributes {
            @Suppress("UnstableApiUsage")
            attribute(
                GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
                project.objects.named(GradleVersion.current().version)
            )
        }
    }
}

dependencies {
    val kotlinVersion = libs.versions.kotlin.version.get()
    implementation(kotlin("gradle-plugin", kotlinVersion))
    implementation(kotlin("serialization", kotlinVersion))

    implementation("io.ktor:ktor-server-default-headers:2.0.2")
    implementation("io.ktor:ktor-server-netty:2.0.2")
    implementation("io.ktor:ktor-server-cio:2.0.2")
    implementation("io.ktor:ktor-server-jetty:2.0.2")
    implementation("io.ktor:ktor-server-websockets:2.0.2")
    implementation("io.ktor:ktor-server-auth:2.0.2")
    implementation("io.ktor:ktor-server-caching-headers:2.0.2")
    implementation("io.ktor:ktor-server-conditional-headers:2.0.2")
    implementation("io.ktor:ktor-server-compression:2.0.2")
    implementation("io.ktor:ktor-server-content-negotiation:2.0.2")
    implementation("io.ktor:ktor-serialization-kotlinx:2.0.2")
    implementation("io.ktor:ktor-network-tls-certificates:2.0.2")

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
}

kotlin {
    jvmToolchain {
        check(this is JavaToolchainSpec)
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}


extra["kotlin_language_version"] = rootProject.properties["kotlin_language_version"]
val kotlin_language_version: String? by extra

extra["kotlin_api_version"] = rootProject.properties["kotlin_api_version"]
val kotlin_api_version: String? by extra

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += listOf(
        "-Xsuppress-version-warnings",
        "-Xskip-metadata-version-check",
        "-version"
    )

    if (kotlin_language_version != null) {
        kotlinOptions.languageVersion = kotlin_language_version
    }
    if (kotlin_language_version != null) {
        kotlinOptions.apiVersion = kotlin_api_version
    }
}

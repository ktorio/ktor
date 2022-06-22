/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "1.6.21"
}

val buildSnapshotTrain = properties["build_snapshot_train"]?.toString()?.toBoolean() == true

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
    implementation(kotlin("gradle-plugin", "1.6.21"))
    implementation(kotlin("serialization", "1.6.21"))

    val ktlint_version = libs.versions.ktlint.version.get()
    implementation("org.jmailen.gradle:kotlinter-gradle:$ktlint_version") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-script-runtime")
    }
    implementation("org.jmailen.gradle:kotlinter-gradle:$ktlint_version")

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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.freeCompilerArgs += listOf(
        "-Xsuppress-version-warnings",
        "-Xskip-metadata-version-check",
    )
}

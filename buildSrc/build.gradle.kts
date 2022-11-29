/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

plugins {
    id("org.gradle.kotlin.kotlin-dsl") version "3.2.1"
    kotlin("plugin.serialization") version "1.7.21"
}

val buildSnapshotTrain = properties["build_snapshot_train"]?.toString()?.toBoolean() == true

repositories {
    if (buildSnapshotTrain) {
        mavenLocal()
    }

    mavenCentral()
    maven("https://plugins.gradle.org/m2")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
}

sourceSets.main {
}

dependencies {
    implementation(kotlin("gradle-plugin", "1.7.21"))
    implementation(kotlin("serialization", "1.7.21"))

    val ktlint_version = libs.versions.ktlint.version.get()
    val ktor_test_version = libs.versions.ktor.test.version.get()

    implementation("org.jmailen.gradle:kotlinter-gradle:$ktlint_version")

    implementation("io.ktor:ktor-server-default-headers:$ktor_test_version")
    implementation("io.ktor:ktor-server-netty:$ktor_test_version")
    implementation("io.ktor:ktor-server-cio:$ktor_test_version")
    implementation("io.ktor:ktor-server-jetty:$ktor_test_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_test_version")
    implementation("io.ktor:ktor-server-auth:$ktor_test_version")
    implementation("io.ktor:ktor-server-caching-headers:$ktor_test_version")
    implementation("io.ktor:ktor-server-conditional-headers:$ktor_test_version")
    implementation("io.ktor:ktor-server-compression:$ktor_test_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_test_version")
    implementation("io.ktor:ktor-serialization-kotlinx:$ktor_test_version")
    implementation("io.ktor:ktor-network-tls-certificates:$ktor_test_version")

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
}

kotlin {
    jvmToolchain {
        check(this is JavaToolchainSpec)
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "End-to-end throughput benchmarks for Ktor client and server engines"

plugins {
    id("ktorbuild.project.internal")
}

tasks.register<JavaExec>("profileNettyApache") {
    description = "Run Netty + Apache5 benchmark for profiling"
    group = "benchmark"

    mainClass.set("io.ktor.benchmark.throughput.ProfileNettyApacheKt")
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
        kotlin.jvm().compilations["main"].output.allOutputs

    // Forward system properties for benchmark configuration
    systemProperties = System.getProperties().mapKeys { it.key.toString() }
        .filterKeys { it.startsWith("benchmark.") }

    // JVM args for better profiling
    jvmArgs = listOf("-Xms512m", "-Xmx2g")
}

tasks.register<JavaExec>("runBigFile") {
    description = "Run big file transfer benchmark for profiling throughput"
    group = "benchmark"

    mainClass.set("io.ktor.benchmark.throughput.ProfileBigFileTransferKt")
    classpath = kotlin.jvm().compilations["main"].runtimeDependencyFiles +
        kotlin.jvm().compilations["main"].output.allOutputs

    // Forward system properties for benchmark configuration
    systemProperties = System.getProperties().mapKeys { it.key.toString() }
        .filterKeys { it.startsWith("benchmark.") }

    // JVM args: larger heap for file operations
    jvmArgs = listOf("-Xms512m", "-Xmx4g")
}

kotlin {
    sourceSets {
        jvmMain.dependencies {
            // Server core
            api(projects.ktorServerCore)
            api(projects.ktorServerHostCommon)

            // Server engines
            implementation(projects.ktorServerNetty)
            implementation(projects.ktorServerCio)
            implementation(projects.ktorServerJetty)
            implementation(projects.ktorServerTomcat)

            // Client core
            api(projects.ktorClientCore)

            // Client engines
            implementation(projects.ktorClientCio)
            implementation(projects.ktorClientOkhttp)
            implementation(projects.ktorClientApache5)
            implementation(projects.ktorClientJava)
            implementation(projects.ktorClientJetty)

            // Coroutines
            implementation(libs.kotlinx.coroutines.core)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test.junit5)
            implementation(libs.junit)
        }
    }
}

/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    kotlin("jvm")
    application
}

group = "io.ktor.test"

dependencies {
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.websockets)
    implementation(ktorLibs.server.sse)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.cachingHeaders)
    implementation(ktorLibs.server.conditionalHeaders)
    implementation(ktorLibs.server.compression)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.serialization.kotlinx)
    implementation(ktorLibs.network.tls.certificates)
    implementation(ktorLibs.utils)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.tomlj)
}

// Should be synced with gradle/gradle-daemon-jvm.properties, as the server runs on the Gradle daemon JVM
kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors = true
    }
}

application {
    // Should be synced with TestServerService.MAIN_CLASS
    mainClass = "test.server.TestServerMainKt"
}

configurations.consumable("testServerLibraries") {
    attributes {
        // Should be synced with TestServerService.SERVER_LIBRARIES_USAGE
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("ktor-test-server-libraries"))
    }
    outgoing.artifact(tasks.installDist.map { it.destinationDir.resolve("lib") }) {
        builtBy(tasks.installDist)
    }
}

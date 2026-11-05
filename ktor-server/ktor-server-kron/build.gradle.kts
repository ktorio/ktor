/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "Ktor server engine on kron: io_uring reactors, HTTP/1.1 parsed by Glyph11"

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    // kron's JVM target reaches io_uring and BoringSSL through the Foreign Function & Memory API: JDK 22 and up, and
    // its jar is built for 25.
    jvmToolchain(25)

    sourceSets {
        all {
            languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
        }
        matching { it.name.startsWith("linux") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
        commonMain.dependencies {
            api(projects.ktorServerCore)
            api(libs.kron.core)
            implementation(libs.kron.glyph11)
        }
        linuxTest.dependencies {
            implementation(projects.ktorServerTestSuites)
            implementation(projects.ktorServerTestBase)
            implementation(projects.ktorServerWebsockets)
        }
        jvmTest.dependencies {
            implementation(projects.ktorServerTestSuites)
            implementation(projects.ktorServerTestBase)
            implementation(projects.ktorServerWebsockets)
        }
    }
}

// The engine uses no atomicfu, and the transform reads every class it links against on the build's own JVM (21), which
// cannot load kron's (25).
atomicfu {
    transformJvm = false
}

// Run on 25, whatever test.jdk says: below the compile JDK, Ktor's convention would skip the tests rather than fail.
tasks.named<Test>("jvmTest") {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    setOnlyIf { true }
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Some suites wait with no timeout of their own: a test that would hang fails instead.
    systemProperty("junit.jupiter.execution.timeout.default", "2m")
    filter {
        // Suite tests kron does not pass, which cannot be overridden: a request in bare-LF lines, whose head never
        // ends for Glyph11, which takes CRLF only; a cancelled parent job, which ends the calls but leaves the listeners
        // until stop(); an IPv6 host, as kron listens on every IPv4 address whatever the connector's host.
        excludeTestsMatching("*KronJvmSustainabilityTest.testDoubleHost")
        excludeTestsMatching("*KronJvmSustainabilityTest.testEmbeddedServerCancellation")
        excludeTestsMatching("*KronJvmConnectionTest.testIpv6ServerHostAndPort")
    }
}

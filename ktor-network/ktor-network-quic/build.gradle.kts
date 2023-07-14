/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("org.jetbrains.kotlinx.kover") version "0.7.1"
}

kotlin {
    sourceSets {
        jvmAndNixMain {
            dependencies {
                api(project(":ktor-network"))
                api(project(":ktor-utils"))
            }
        }
        jvmMain {
            dependencies {
                // temporary replacement for TLS implementation
                implementation(files("lib/agent15.jar"))
                implementation("at.favre.lib:hkdf:1.0.1") // dependency of agent15
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(project(":ktor-network:ktor-network-test"))
            }
        }
    }

    /*
     * IP_DONTFRAGMENT was added in java 19, see https://bugs.openjdk.org/browse/JDK-8284890
     * it is required (if possible) for the QUIC implementation, https://www.rfc-editor.org/rfc/rfc9000.html#name-datagram-size
     */
    // todo requires gradle 7.6
//    jvmToolchain(19)
}

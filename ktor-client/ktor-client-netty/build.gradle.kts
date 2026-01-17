/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("ktorbuild.project.library")
    id("test-server")
}


val enableAlpnProp = project.hasProperty("enableAlpn")
val osName = System.getProperty("os.name").lowercase()
val nativeClassifier: String? = if (enableAlpnProp) {
    when {
        osName.contains("win") -> "windows-x86_64"
        osName.contains("linux") -> "linux-x86_64"
        osName.contains("mac") -> "osx-x86_64"
        else -> throw InvalidUserDataException("Unsupported os family $osName")
    }
} else {
    null
}
kotlin {
    // Package java.net.http was introduced in Java 11
    jvmToolchain(11)

    sourceSets {
        jvmMain.dependencies {
            api(projects.ktorClientCore)

            api(libs.netty.codec.http2)
            api(libs.jetty.alpn.api)

            api(libs.netty.transport.native.kqueue)
            api(libs.netty.transport.native.epoll)
            if (nativeClassifier != null) {
                api(libs.netty.tcnative.boringssl.static)
            }
        }

        jvmTest.dependencies {
            api(projects.ktorClientTests)

            api(libs.netty.tcnative)
            api(libs.netty.tcnative.boringssl.static)
            api(libs.mockk)
        }
    }
}

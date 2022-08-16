import test.server.*

/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

description = "Common tests for client"

plugins {
    id("kotlinx-serialization")
}

apply<TestServerPlugin>()

val osName = System.getProperty("os.name")

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-mock"))
            api(project(":ktor-test-dispatcher"))
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-serialization"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-auth"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-encoding"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-serialization"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-json"))
        }
    }
    jvmMain {
        dependencies {
            api(libs.kotlinx.serialization.json)
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(project(":ktor-server"))
            api(project(":ktor-server:ktor-server-cio"))
            api(project(":ktor-server:ktor-server-netty"))
            api(project(":ktor-server:ktor-server-jetty"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-auth"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-websockets"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(libs.logback.classic)
            api(libs.junit)
            api(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-apache"))
            runtimeOnly(project(":ktor-client:ktor-client-android"))
            runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
            if (currentJdk >= 11) {
                runtimeOnly(project(":ktor-client:ktor-client-java"))
            }
            implementation(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
            implementation(libs.kotlinx.coroutines.slf4j)
        }
    }

    jvmAndNixTest {
        dependencies {
            runtimeOnly(project(":ktor-client:ktor-client-cio"))
        }
    }

    jsTest {
        dependencies {
            api(project(":ktor-client:ktor-client-js"))
        }
    }

    desktopTest {
        dependencies {
            api(project(":ktor-client:ktor-client-curl"))
        }
    }

    darwinTest {
        dependencies {
            api(project(":ktor-client:ktor-client-darwin"))
            api(project(":ktor-client:ktor-client-darwin-legacy"))
        }
    }
}

useJdkVersionForJvmTests(11)

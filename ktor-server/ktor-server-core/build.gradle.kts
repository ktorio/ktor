/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-utils"))
            api(project(":ktor-http"))
            api(project(":ktor-shared:ktor-serialization"))
            api(project(":ktor-shared:ktor-events"))

            api(libs.kotlin.reflect)
        }
    }

    jvmMain {
        dependencies {
            api(libs.typesafe.config)
            implementation(libs.jansi)
        }
    }

    jvmAndNixTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(libs.logback.classic)
            api(project(":ktor-network"))
        }
    }

    jvmTest {
        dependencies {
            implementation(libs.mockk)
        }
    }
}

artifacts {
    val jarTest by tasks
    add("testOutput", jarTest)
}

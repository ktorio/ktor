/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

val typesafe_config_version: String by extra
val kotlin_version: String by extra
val mockk_version: String by extra
val jansi_version: String by extra
val logback_version: String by extra

kotlin.sourceSets {
    jvmAndNixMain {
        dependencies {
            api(project(":ktor-utils"))
            api(project(":ktor-http"))
            api(project(":ktor-shared:ktor-serialization"))
            api(project(":ktor-shared:ktor-events"))

            api("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
        }
    }

    jvmMain {
        dependencies {
            api("com.typesafe:config:$typesafe_config_version")
            implementation("org.fusesource.jansi:jansi:$jansi_version")
        }
    }

    val jvmAndNixTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api("ch.qos.logback:logback-classic:$logback_version")
            api(project(":ktor-network"))
        }
    }

    jvmTest {
        dependencies {
            implementation("io.mockk:mockk:$mockk_version")
        }
    }
}

artifacts {
    val jarTest by tasks
    add("testOutput", jarTest)
}

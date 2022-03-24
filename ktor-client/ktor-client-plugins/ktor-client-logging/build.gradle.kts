/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

val slf4j_version: String by project.extra

kotlin.sourceSets {
    jvmMain {
        dependencies {
            compileOnly("org.slf4j:slf4j-simple:$slf4j_version")
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-mock"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-jackson"))
        }
    }
}

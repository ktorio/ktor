/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

description = "Ktor client features"

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
}

subprojects {
    kotlin.sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
            }
        }

        val jvmTest by getting {
            dependencies {
                runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
                runtimeOnly(project(":ktor-client:ktor-client-apache"))
                runtimeOnly(project(":ktor-client:ktor-client-cio"))
                runtimeOnly(project(":ktor-client:ktor-client-android"))
                runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
                try {
                    runtimeOnly(project(":ktor-client:ktor-client-java"))
                } catch (_: UnknownProjectException) {
                }
//            runtimeOnly(project(":ktor-client:ktor-client-jetty"))
            }
        }

        findByName("jsTest")?.dependencies {
            api(project(":ktor-client:ktor-client-js"))
        }

        val commonTest by getting {
            dependencies {
                api(project(":ktor-client:ktor-client-tests"))
            }
        }
    }
}

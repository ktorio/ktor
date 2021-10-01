/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""
val jansi_version: String by project.extra

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("org.fusesource.jansi:jansi:$jansi_version")
            }
        }
    }
}

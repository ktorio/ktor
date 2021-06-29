/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api("io.pebbletemplates:pebble:3.1.5")
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server-features:ktor-server-conditional-headers"))
            api(project(":ktor-server-features:ktor-server-compression"))
        }
    }
}

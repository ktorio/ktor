/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin {
    sourceSets {
        posixMain {
            dependencies {
                implementation(project(":ktor-utils"))
            }
        }
    }
}

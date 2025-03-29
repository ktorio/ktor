/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":ktor-io"))
                api(project(":ktor-utils"))
            }
        }
    }
}

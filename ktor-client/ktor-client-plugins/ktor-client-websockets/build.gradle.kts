/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin.sourceSets {
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
        }
    }
}

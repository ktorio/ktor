/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

description = "Ktor client Basic Auth support"

project.ext.set("commonStructure", false)

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-auth"))
        }
    }
}

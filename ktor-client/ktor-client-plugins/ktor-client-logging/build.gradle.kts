/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

val slf4j_version: String by project.extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            compileOnly("org.slf4j:slf4j-simple:$slf4j_version")
        }
    }
}

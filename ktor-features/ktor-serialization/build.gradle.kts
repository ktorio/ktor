/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

val serialization_version: String by extra

plugins {
    id("kotlinx-serialization")
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version")
        }
    }
}

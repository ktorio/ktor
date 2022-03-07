/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    kotlin("plugin.serialization") version "1.6.10"
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}

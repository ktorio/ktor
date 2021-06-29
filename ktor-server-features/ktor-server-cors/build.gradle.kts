/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = ""

val mockk_version: String by project.extra

kotlin.sourceSets {
    val jvmTest by getting {
        dependencies {
            implementation("io.mockk:mockk:$mockk_version")
        }
    }
}

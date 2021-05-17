/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

kotlin {
    sourceSets {
        val jvmMain by getting {
            dependencies {
                api("org.apache.velocity:velocity-engine-core:2.3")
                api("org.apache.velocity.tools:velocity-tools-generic:3.1")
            }
        }
    }
}

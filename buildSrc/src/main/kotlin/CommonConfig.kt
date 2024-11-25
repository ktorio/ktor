/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import internal.*
import org.gradle.api.*
import org.gradle.kotlin.dsl.*

fun Project.configureCommon() {
    kotlin {
        sourceSets {
            commonMain {
                dependencies {
                    api(libs.kotlinx.coroutines.core)
                }
            }

            commonTest {
                dependencies {
                    implementation(libs.kotlin.test)
                }
            }
        }
    }
}

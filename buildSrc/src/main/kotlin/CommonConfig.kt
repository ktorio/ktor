/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import org.gradle.api.*
import org.gradle.kotlin.dsl.*

fun Project.configureCommon() {
    val coroutines_version: String by extra

    kotlin {
        sourceSets {
            val commonMain by getting {
                dependencies {
                    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
                }
            }

            val commonTest by getting {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }
    }
}

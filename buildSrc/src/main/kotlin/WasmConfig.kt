/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import internal.*
import org.gradle.api.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.*
import org.jetbrains.kotlin.gradle.targets.js.ir.*
import java.io.*

fun Project.configureWasm() {
    kotlin {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            nodejs { useMochaForTests() }
            if (project.targetIsEnabled("wasmJs.browser")) browser { useKarmaForTests() }
        }

        sourceSets {
            wasmJsMain {
                dependencies {
                    implementation(libs.kotlinx.browser)
                }
            }
            wasmJsTest {
                dependencies {
                    implementation(npm("puppeteer", libs.versions.puppeteer.get()))
                }
            }
        }
    }

    configureJsTestTasks(target = "wasmJs")
}

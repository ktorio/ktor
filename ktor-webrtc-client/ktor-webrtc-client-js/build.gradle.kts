@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("ktorbuild.project.library")
}

kotlin {
    sourceSets {
        jsAndWasmSharedMain {
            dependencies {
                api(project(":ktor-webrtc-client:ktor-webrtc-client-core"))
            }
        }

        wasmJs {
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-attach-js-exception")
            }
        }
    }
}

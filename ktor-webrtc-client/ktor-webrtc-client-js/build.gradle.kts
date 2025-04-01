@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

kotlin {
    sourceSets {
        jsAndWasmSharedMain {
            dependencies {
                api(project(":ktor-webrtc-client:ktor-webrtc-client-core"))
            }
        }

        wasmJs {
            browser()
            compilerOptions {
                freeCompilerArgs.add("-Xwasm-attach-js-exception")
            }
        }
    }
}

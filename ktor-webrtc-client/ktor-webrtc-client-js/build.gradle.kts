kotlin {
    sourceSets {
        jsAndWasmSharedMain {
            dependencies {
                api(project(":ktor-webrtc-client:ktor-webrtc-client-core"))
            }
        }
    }
}

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-network:ktor-network-tls"))
        }
    }
}

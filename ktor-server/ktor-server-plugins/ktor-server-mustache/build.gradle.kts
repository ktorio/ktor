
kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(libs.mustache)
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
            implementation(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
        }
    }
}

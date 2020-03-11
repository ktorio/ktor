kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    jvmTest {
        dependencies {
            implementation(project(":ktor-client:ktor-client-cio"))
        }
    }
}

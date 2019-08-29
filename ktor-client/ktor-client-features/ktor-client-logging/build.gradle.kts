val slf4j_version: String by project.extra

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    jvmMain {
        dependencies {
            compileOnly("org.slf4j:slf4j-simple:$slf4j_version")
        }
    }

    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
    jvmTest {
        dependencies {
            implementation(project(":ktor-client:ktor-client-cio"))
            implementation(project(":ktor-client:ktor-client-okhttp"))
            implementation(project(":ktor-client:ktor-client-android"))
            implementation(project(":ktor-client:ktor-client-apache"))
        }
    }
}

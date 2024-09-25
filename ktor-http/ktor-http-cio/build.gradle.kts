description = ""

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-io"))
        }
    }

    jvmMain {
        dependencies {
            api(project(":ktor-network"))
        }
    }

    jvmTest {
        dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

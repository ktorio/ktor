description = ""

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-http"))
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

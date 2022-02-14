description = ""

val coroutines_version: String by extra

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
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version")
        }
    }
}

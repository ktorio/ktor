description = ""

extra["commonStructure"] = false

val coroutines_version: String by extra

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-http"))
        }
    }

    val jvmMain by getting {
        dependencies {
            api(project(":ktor-network"))
        }
    }

    val jvmTest by getting {
        dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutines_version")
        }
    }
}

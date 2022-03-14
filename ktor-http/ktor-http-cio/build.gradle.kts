description = ""

extra["commonStructure"] = false

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
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

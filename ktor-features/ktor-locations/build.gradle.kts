kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-features:ktor-auth"))
        }
    }
}

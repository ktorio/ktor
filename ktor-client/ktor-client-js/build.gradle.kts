kotlin.sourceSets {
    val jsMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
}

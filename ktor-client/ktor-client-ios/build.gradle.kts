kotlin.sourceSets {
    val darwinMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-darwin"))
        }
    }
}

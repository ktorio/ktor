description = "Ktor client features"

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
}

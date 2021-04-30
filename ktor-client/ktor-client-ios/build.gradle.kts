
val ideaActive: Boolean by project.extra
val serialization_version: String by project.extra

kotlin.sourceSets {
    darwinMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
}

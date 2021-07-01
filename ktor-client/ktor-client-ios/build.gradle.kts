
val ideaActive: Boolean by project.extra
val serialization_version: String by project.extra

kotlin.sourceSets {
    val darwinMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    val darwinTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
        }
    }
}

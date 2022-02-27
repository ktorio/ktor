
kotlin.sourceSets {
    darwinMain {
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

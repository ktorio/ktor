
kotlin.sourceSets {
    val darwinMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    val darwinTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-logging"))
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
        }
    }
}

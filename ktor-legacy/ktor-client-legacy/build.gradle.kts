description = "Ktor http client legacy code and deprecations"

kotlin.sourceSets {
    val commonMain by getting {
        dependencies {
            api(project(":ktor-http"))
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-shared:ktor-shared-events"))
        }
    }
}

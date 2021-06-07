val gson_version: String by project.extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))
            api("com.google.code.gson:gson:$gson_version")
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-client:ktor-client-features:ktor-client-json:ktor-client-json-tests"))
            api(project(":ktor-features:ktor-gson"))
        }
    }
}

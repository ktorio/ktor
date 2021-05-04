
val gson_version: String by extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-utils"))
            api("com.google.code.gson:gson:$gson_version")
        }
    }
}

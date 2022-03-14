
kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-utils"))
            api(libs.gson)
        }
    }
}

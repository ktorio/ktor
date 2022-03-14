
kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            api(libs.okhttp)
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}

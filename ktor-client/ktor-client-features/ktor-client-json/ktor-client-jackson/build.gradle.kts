
kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-features:ktor-client-json"))

            api(libs.jackson.databind)
            api(libs.jackson.module.kotlin)
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

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-features:ktor-auth"))
        }
    }
    val jvmTest by getting {
        dependencies {
            api(libs.apacheds.server)
            api(libs.apacheds.core)
        }
    }
}

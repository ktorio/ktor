
kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-features:ktor-auth"))
            api(libs.json.simple)
            api(libs.java.jwt)
            api(libs.jwks.rsa)
        }
    }
    val jvmTest by getting {
        dependencies {
            api(libs.mokito.kotlin)
        }
    }
}


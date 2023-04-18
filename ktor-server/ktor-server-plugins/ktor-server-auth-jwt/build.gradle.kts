kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-auth"))
            api(libs.java.jwt)
            api(libs.jwks.rsa)
        }
    }
    jvmTest {
        dependencies {
            api(libs.mockk)
        }
    }
}


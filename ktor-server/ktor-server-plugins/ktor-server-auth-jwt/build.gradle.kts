val json_simple_version: String by project.extra
val java_jwt_version: String by project.extra
val jwks_rsa_version: String by project.extra
val mokito_kotlin_version: String by project.extra

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
            api(libs.mockito.kotlin)
        }
    }
}


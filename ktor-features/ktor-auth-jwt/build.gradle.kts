val json_simple_version: String by project.extra
val java_jwt_version: String by project.extra
val jwks_rsa_version: String by project.extra
val mokito_kotlin_version: String by project.extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-features:ktor-auth"))
            api("com.googlecode.json-simple:json-simple:$json_simple_version")
            api("com.auth0:java-jwt:$java_jwt_version")
            api("com.auth0:jwks-rsa:$jwks_rsa_version")
        }
    }
    val jvmTest by getting {
        dependencies {
            api("com.nhaarman:mockito-kotlin:$mokito_kotlin_version")
        }
    }
}


ext {
    set(RELOCATED_ARTIFACT, "ktor-auth-ldap")
}

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-auth"))
        }
    }
    jvmTest {
        dependencies {
            api(libs.apacheds.server)
            api(libs.apacheds.core)
        }
    }
}

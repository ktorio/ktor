
kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api("org.thymeleaf:thymeleaf:[3.0.11.RELEASE, 3.1)")
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server-features:ktor-server-conditional-headers"))
            api(project(":ktor-server-features:ktor-server-compression"))
        }
    }
}

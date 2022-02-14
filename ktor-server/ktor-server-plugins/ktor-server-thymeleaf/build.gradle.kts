
kotlin.sourceSets {
    jvmMain {
        dependencies {
            api("org.thymeleaf:thymeleaf:[3.0.11.RELEASE, 3.1)")
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
        }
    }
}

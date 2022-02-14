description = ""
val mockk_version: String by extra

kotlin.sourceSets {
    jvmMain {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))

            compileOnly("javax.servlet:javax.servlet-api:4.0.1")
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            implementation("io.mockk:mockk:$mockk_version")
            implementation("javax.servlet:javax.servlet-api:4.0.1")
        }
    }
}

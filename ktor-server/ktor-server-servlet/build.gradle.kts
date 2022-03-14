description = ""

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-host-common"))
            api(project(":ktor-http:ktor-http-cio"))

            compileOnly(libs.javax.servlet)
        }
    }

    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
            implementation(libs.mockk)
            implementation(libs.javax.servlet)
        }
    }
}

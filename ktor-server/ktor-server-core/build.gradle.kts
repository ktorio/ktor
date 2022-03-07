description = ""

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-utils"))
            api(project(":ktor-http"))

            api(libs.typesafe.config)
            api(libs.kotlin.reflect)
            implementation(libs.jansi)
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-network"))
            api(project(":ktor-server:ktor-server-test-host"))
            implementation(libs.mockk)
        }
    }
}

artifacts {
    val jarTest by tasks
    add("testOutput", jarTest)
}

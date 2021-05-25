description = ""

val typesafe_config_version: String by extra
val kotlin_version: String by extra
val mockk_version: String by extra

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-utils"))
            api(project(":ktor-http"))

            api("com.typesafe:config:$typesafe_config_version")
            api("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-http:ktor-http-cio"))
            api(project(":ktor-network"))
            api(project(":ktor-server:ktor-server-test-host"))
            implementation("io.mockk:mockk:$mockk_version")
        }
    }
}

artifacts {
    val jarTest by tasks
    add("testOutput", jarTest)
}

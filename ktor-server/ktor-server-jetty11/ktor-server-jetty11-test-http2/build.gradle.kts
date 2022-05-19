
kotlin.sourceSets {
    jvmTest {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(libs.jetty.servlet)
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-jetty11"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))

            api(libs.logback.classic)
        }
    }
}

val jvmTest: org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest by tasks
jvmTest.apply {
    useJUnit()

    systemProperty("enable.http2", "true")
    exclude("**/*StressTest*")
}

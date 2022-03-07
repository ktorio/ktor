
kotlin.sourceSets {
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api(libs.jetty.servlet)
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-jetty"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}

val need_alpn_boot: Boolean by extra
dependencies {
    if (need_alpn_boot) {
        add("boot", libs.jetty.alpn.boot)
    }
}

val jvmTest: org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest by tasks
jvmTest.apply {
    useJUnit()

    systemProperty("enable.http2", "true")
    exclude("**/*StressTest*")

    if (need_alpn_boot && JavaVersion.current() == JavaVersion.VERSION_1_8) {
        val bootClasspath = configurations.named("boot").get().files
        jvmArgs(bootClasspath.map { "-Xbootclasspath/p:${it.absolutePath}" }.iterator())
    }
}

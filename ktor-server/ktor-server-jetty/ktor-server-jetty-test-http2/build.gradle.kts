val jetty_version: String by extra

kotlin.sourceSets {
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-server:ktor-server-test-host"))
            api(project(":ktor-server:ktor-server-test-suites"))
            api("org.eclipse.jetty:jetty-servlet:$jetty_version")
            api(project(":ktor-server:ktor-server-core"))
            api(project(":ktor-server:ktor-server-jetty"))
            api(project(":ktor-server:ktor-server-core", configuration = "testOutput"))
        }
    }
}

val jetty_alpn_boot_version: String? by extra
dependencies {
    if (jetty_alpn_boot_version != null) {
        add("boot", "org.mortbay.jetty.alpn:alpn-boot:$jetty_alpn_boot_version")
    }
}

val jvmTest: org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest by tasks
jvmTest.apply {
    useJUnit()

    systemProperty("enable.http2", "true")
    exclude("**/*StressTest*")

    if (jetty_alpn_boot_version != null && JavaVersion.current() == JavaVersion.VERSION_1_8) {
        val bootClasspath = configurations.named("boot").get().files
        jvmArgs(bootClasspath.map { "-Xbootclasspath/p:${it.absolutePath}" }.iterator())
    }
}

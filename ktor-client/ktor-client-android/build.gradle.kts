kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
        }
    }
    val jvmTest by getting {
        dependencies {
            api(project(":ktor-client:ktor-client-tests"))
            api(project(":ktor-network:ktor-network-tls"))
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
        }
    }
}

// pass JVM option to enlarge built-in HttpUrlConnection pool
// to avoid failures due to lack of local socket ports
val jvmTestTasks = if (rootProject.ext.get("build_snapshot_train") as Boolean) {
    listOf(tasks.jvmTest, tasks.named<org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest>("jvmIrTest"))
} else {
    listOf(tasks.jvmTest)
}
configure(jvmTestTasks) {
    configure {
        useJUnit()

        jvmArgs("-Dhttp.maxConnections=32")
    }
}

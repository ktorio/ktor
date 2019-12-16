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
configure(listOf(tasks.jvmTest)) {
    configure {
        useJUnit()

        jvmArgs("-Dhttp.maxConnections=32")
    }
}

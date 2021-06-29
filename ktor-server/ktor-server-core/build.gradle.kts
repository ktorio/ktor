description = ""

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server:ktor-server"))
            api(project(":ktor-server-features:ktor-server-auto-head-response"))
            api(project(":ktor-server-features:ktor-server-caching-headers"))
            api(project(":ktor-server-features:ktor-server-call-id"))
            api(project(":ktor-server-features:ktor-server-call-logging"))
            api(project(":ktor-server-features:ktor-server-compression"))
            api(project(":ktor-server-features:ktor-server-conditional-headers"))
            api(project(":ktor-server-features:ktor-server-content-negotiation"))
            api(project(":ktor-server-features:ktor-server-cors"))
            api(project(":ktor-server-features:ktor-server-data-conversion"))
            api(project(":ktor-server-features:ktor-server-default-headers"))
            api(project(":ktor-server-features:ktor-server-double-receive"))
            api(project(":ktor-server-features:ktor-server-forwarded-header"))
            api(project(":ktor-server-features:ktor-server-hsts"))
            api(project(":ktor-server-features:ktor-server-http-redirect"))
            api(project(":ktor-server-features:ktor-server-partial-content"))
            api(project(":ktor-server-features:ktor-server-sessions"))
            api(project(":ktor-server-features:ktor-server-status-pages"))
        }
    }
}

artifacts {
    val jarTest by tasks
    add("testOutput", jarTest)
}

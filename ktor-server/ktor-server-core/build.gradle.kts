description = ""

kotlin.sourceSets {
    val jvmMain by getting {
        dependencies {
            api(project(":ktor-server"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-auto-head-response"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-caching-headers"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-call-id"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-call-logging"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-compression"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-conditional-headers"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-content-negotiation"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-cors"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-data-conversion"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-default-headers"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-double-receive"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-forwarded-header"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-hsts"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-http-redirect"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-partial-content"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-sessions"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            api(project(":ktor-legacy:ktor-server-legacy"))
        }
    }
}

artifacts {
    val jarTest by tasks
    add("testOutput", jarTest)
}
